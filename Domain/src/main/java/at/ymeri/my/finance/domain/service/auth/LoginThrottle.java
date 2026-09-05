package at.ymeri.my.finance.domain.service.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed authentication attempts and decides when to stop answering them.
 *
 * <p><strong>Why an app with one user throttles at all.</strong> Until feature 023 there was a
 * deployment story but no easy deployment; now there is, and the login page can be reached by
 * anyone who has the URL. Unlimited guessing against a single credential protecting an entire
 * financial history is not a theoretical exposure once that is true.
 *
 * <p><strong>The asymmetry that shapes every rule here.</strong> This app has one account and no
 * password-reset email, so locking out the account and locking out the operator are the same event.
 * Refusing too little leaves a stranger guessing; refusing too much destroys the operator's access
 * to their own records with no way back. The second is worse — an attack that might succeed against
 * a loss that certainly has — so every refusal expires by the clock alone. There is no
 * administrative endpoint to lift one, deliberately: a lockout only a developer could clear is one
 * an operator cannot.
 *
 * <p><strong>Two tiers, because one account makes the obvious answer wrong.</strong> Keying on the
 * username is the usual approach and is useless here: with a single account it is a global counter,
 * which means a public button labelled "lock the owner out". Keying only on the caller is defeated
 * by anyone who thinks to rotate. So: a low per-caller threshold for the ordinary case, and a high
 * instance-wide one for the rotating case. The residual risk is real and accepted — a determined
 * distributed attacker can keep the instance-wide tier tripped and so keep the operator out for as
 * long as they care to continue. That is a denial of service rather than a break-in, the thresholds
 * are configurable, and the alternative is letting distributed guessing run unbounded against
 * financial records.
 *
 * <p><strong>Nothing is persisted.</strong> The state here describes a moment, not a fact about the
 * operator's money. In the database it would sit inside their backups and make every login a write;
 * losing it on restart costs an attacker a restart they cannot cause.
 *
 * <p>Takes {@code Instant now} as a parameter rather than reading a clock, which is what makes
 * "the refusal expires after fifteen minutes" a test that runs in a millisecond.
 */
@Component
public class LoginThrottle {

    /** The instance-wide tier is the same structure under a key no caller can present. */
    private static final String INSTANCE_WIDE = " instance-wide";

    /**
     * A ceiling on tracked keys. The per-caller key comes from whoever is calling, so without this
     * the map is a memory-exhaustion vector that this class would have introduced itself. Reaching
     * it means an attack is in progress, and the instance-wide tier is already refusing.
     */
    private static final int MAX_TRACKED_KEYS = 10_000;

    /** How long a key with no refusal in force is remembered before being forgotten. */
    private static final Duration IDLE_RETENTION = Duration.ofHours(1);

    private final Settings settings;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** For tests, which supply their own thresholds rather than reading configuration. */
    public LoginThrottle(Settings settings) {
        this.settings = settings;
    }

    /** The constructor Spring uses. Marked, because two candidates are otherwise ambiguous. */
    @Autowired
    public LoginThrottle(
            @Value("${app.security.login-throttle.enabled:true}") boolean enabled,
            @Value("${app.security.login-throttle.per-caller-threshold:5}") int perCallerThreshold,
            @Value("${app.security.login-throttle.instance-threshold:20}") int instanceThreshold,
            @Value("${app.security.login-throttle.window-minutes:15}") long windowMinutes) {
        this(new Settings(enabled, perCallerThreshold, instanceThreshold,
                Duration.ofMinutes(windowMinutes)));
    }

    /**
     * Whether this caller is currently being refused, by either tier.
     *
     * <p>Callers MUST consult this <em>before</em> verifying the submitted password. A refusal that
     * still runs the password hash takes measurably longer than one that does not, which turns the
     * response time into an oracle and gives back what the refusal was protecting.
     */
    public boolean isRefused(String caller, Instant now) {
        if (!settings.enabled()) {
            return false;
        }
        return refusing(caller, now) || refusing(INSTANCE_WIDE, now);
    }

    /**
     * Records a failed attempt against both tiers.
     *
     * <p><strong>An attempt made while already refusing is not recorded at all.</strong> It was
     * never evaluated — {@link #isRefused} answered before the password was looked at — so it is
     * not a failed authentication, and counting it would be counting our own refusal. The
     * consequence if it were counted is the one this class exists to prevent: an attacker from a
     * single address, already refused, would go on feeding the instance-wide tier until it tripped
     * too, locking out every caller including the operator. A per-tier version of this rule is not
     * enough, because the tier that is still counting is the one that does the damage.
     */
    public void recordFailure(String caller, Instant now) {
        if (!settings.enabled() || isRefused(caller, now)) {
            return;
        }
        evictExpired(now);
        count(caller, settings.perCallerThreshold(), now);
        count(INSTANCE_WIDE, settings.instanceThreshold(), now);
    }

    /** Clears this caller's history. The instance-wide tier is deliberately left alone. */
    public void recordSuccess(String caller) {
        attempts.remove(caller);
    }

    /** Visible for testing: how many keys are currently tracked. */
    int trackedKeys() {
        return attempts.size();
    }

    private boolean refusing(String key, Instant now) {
        Attempts current = attempts.get(key);
        return current != null && current.isRefusing(now);
    }

    /**
     * <strong>The rule that keeps a refusal temporary.</strong> An attempt made while already
     * refusing is ignored entirely — not counted, and above all not used to push the window
     * further out. Without this, an attacker who never stops renews the refusal forever and the
     * operator never gets back in: the temporary lockout silently becomes the permanent one this
     * whole class is built to avoid.
     */
    private void count(String key, int threshold, Instant now) {
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.isSpent(now)) {
                return Attempts.first(now);
            }
            return current.plusOne(threshold, now, settings.window());
        });
    }

    private void evictExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> entry.getValue().isSpent(now));
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            // Only reachable mid-attack, when the instance-wide tier is already refusing, so
            // dropping per-caller history costs an attacker nothing they were not already denied.
            attempts.keySet().removeIf(key -> !INSTANCE_WIDE.equals(key));
        }
    }

    /**
     * One key's history: how many consecutive failures, when the last one was, and — once the
     * threshold is passed — when the refusal ends.
     */
    private record Attempts(int consecutiveFailures, Instant lastFailure, Instant refusedUntil) {

        static Attempts first(Instant now) {
            return new Attempts(1, now, null);
        }

        Attempts plusOne(int threshold, Instant now, Duration window) {
            int failures = consecutiveFailures + 1;
            Instant until = failures >= threshold ? now.plus(window) : null;
            return new Attempts(failures, now, until);
        }

        boolean isRefusing(Instant now) {
            return refusedUntil != null && now.isBefore(refusedUntil);
        }

        /**
         * Whether this entry has stopped describing anything current, in which case the next
         * failure starts a fresh count rather than continuing this one.
         *
         * <p>Two ways to get here, and the first is easy to miss. <strong>A refusal that has
         * served its window is spent</strong> — the slate is clean. Carrying the old count forward
         * instead would mean the operator, having waited out their lockout, is refused again on
         * their very next typo, and on every typo after that, forever. The second way is simply
         * that nothing has happened here for a long time.
         */
        boolean isSpent(Instant now) {
            if (refusedUntil != null) {
                return !now.isBefore(refusedUntil);
            }
            return lastFailure.plus(IDLE_RETENTION).isBefore(now);
        }
    }

    /**
     * Operator settings (FR-007), with defaults chosen to be safe on a public URL: five failures
     * survives ordinary mistyping and ends casual guessing; twenty is high enough that one
     * operator's bad morning cannot reach it; fifteen minutes makes guessing impractical while
     * leaving a locked-out operator waiting rather than despairing.
     */
    public record Settings(boolean enabled, int perCallerThreshold, int instanceThreshold,
                           Duration window) {
    }
}
