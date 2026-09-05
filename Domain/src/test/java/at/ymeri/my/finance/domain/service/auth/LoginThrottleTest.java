package at.ymeri.my.finance.domain.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When failed logins stop being answered, and — the part that matters more — when they start being
 * answered again.
 *
 * <p>Plain JUnit, no database, no clock. Every scenario here is a decision over an {@link Instant}
 * the test supplies, which is the only reason "the refusal expires after fifteen minutes" is a test
 * anyone will keep rather than one somebody deletes for taking fifteen minutes.
 *
 * <p><strong>The asymmetry this class exists to protect.</strong> Passbook has one account and no
 * password-reset email. Refusing too little leaves a stranger guessing at someone's financial
 * history; refusing too much locks the owner out of it with no way back. The second is worse — an
 * attack that might succeed against a permanent loss that certainly has. Every test below about
 * recovery is testing that asymmetry, not politeness.
 */
class LoginThrottleTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final String CALLER = "198.51.100.7";
    private static final String OTHER_CALLER = "203.0.113.9";

    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        // Small numbers, so a test reads as a scenario rather than as a loop.
        throttle = new LoginThrottle(new LoginThrottle.Settings(
                true, 5, 20, Duration.ofMinutes(15)));
    }

    private void fail(String caller, int times, Instant at) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(caller, at);
        }
    }

    // --- quickstart 1: guessing stops being free -------------------------------------------------

    @Test
    void attemptsBelowTheThresholdAreNotRefused() {
        fail(CALLER, 4, T0);

        assertThat(throttle.isRefused(CALLER, T0)).isFalse();
    }

    @Test
    void theAttemptAfterTheThresholdIsRefused() {
        fail(CALLER, 5, T0);

        assertThat(throttle.isRefused(CALLER, T0)).isTrue();
    }

    // --- quickstart 2: the operator's own typo is not a trap --------------------------------------

    @Test
    void aSuccessClearsTheCount() {
        // Otherwise an operator who mistypes twice this morning and three times this afternoon is
        // refused for a reason they cannot possibly connect to anything they did.
        fail(CALLER, 4, T0);
        throttle.recordSuccess(CALLER);

        fail(CALLER, 4, T0.plus(Duration.ofHours(3)));

        assertThat(throttle.isRefused(CALLER, T0.plus(Duration.ofHours(3)))).isFalse();
    }

    // --- quickstart 3: the refusal ends by itself -------------------------------------------------

    @Nested
    class RecoveryIsUnattended {

        @Test
        void stillRefusedInsideTheWindow() {
            fail(CALLER, 5, T0);

            assertThat(throttle.isRefused(CALLER, T0.plus(Duration.ofMinutes(10)))).isTrue();
        }

        @Test
        void acceptedOnceTheWindowHasPassed() {
            // FR-003 and SC-002. No operator action, no endpoint, no database edit — the clock is
            // the whole mechanism. There is no fourth state and no administrative escape hatch,
            // because a lockout only a developer could clear is one an operator cannot.
            fail(CALLER, 5, T0);

            assertThat(throttle.isRefused(CALLER, T0.plus(Duration.ofMinutes(16)))).isFalse();
        }

        @Test
        void andTheCountStartsAgainFromZero() {
            fail(CALLER, 5, T0);
            Instant later = T0.plus(Duration.ofMinutes(16));

            fail(CALLER, 4, later);

            assertThat(throttle.isRefused(CALLER, later)).isFalse();
        }
    }

    // --- quickstart 4: a relentless attacker cannot make it permanent -----------------------------

    @Test
    void attemptsDuringARefusalDoNotExtendIt() {
        // THE test. Without this rule an attacker who never stops renews the window forever, and
        // the fifteen-minute refusal quietly becomes the permanent lockout the whole design is
        // built to avoid. It fails silently: everything looks correct, and the operator simply
        // never gets back in.
        fail(CALLER, 5, T0);

        for (int minute = 0; minute < 15; minute++) {
            throttle.recordFailure(CALLER, T0.plus(Duration.ofMinutes(minute)));
        }

        assertThat(throttle.isRefused(CALLER, T0.plus(Duration.ofMinutes(16))))
                .as("the window must end where it was first set, not where the attacker stopped")
                .isFalse();
    }

    @Test
    void attemptsDuringARefusalAreStillRefused() {
        fail(CALLER, 5, T0);

        throttle.recordFailure(CALLER, T0.plus(Duration.ofMinutes(1)));

        assertThat(throttle.isRefused(CALLER, T0.plus(Duration.ofMinutes(2)))).isTrue();
    }

    // --- quickstart 6: rotating the caller does not reset the ceiling -----------------------------

    @Nested
    class TwoTiers {

        @Test
        void refusingOneCallerDoesNotRefuseAnother() {
            fail(CALLER, 5, T0);

            assertThat(throttle.isRefused(OTHER_CALLER, T0)).isFalse();
        }

        @Test
        void enoughFailuresAcrossCallersRefuseEveryone() {
            // What an attacker rotating addresses still runs into. Note what this also proves:
            // they CAN deny the operator access this way. That is a known and accepted residual
            // risk (research R2) — a denial of service, not a break-in — and the threshold is
            // configurable precisely so an operator who disagrees can move it.
            for (int caller = 0; caller < 5; caller++) {
                fail("caller-" + caller, 4, T0);
            }

            assertThat(throttle.isRefused("a-caller-that-never-failed", T0)).isTrue();
        }

        @Test
        void theInstanceWideTierAlsoExpiresOnItsOwn() {
            for (int caller = 0; caller < 5; caller++) {
                fail("caller-" + caller, 4, T0);
            }

            assertThat(throttle.isRefused("anyone", T0.plus(Duration.ofMinutes(16)))).isFalse();
        }
    }

    // --- quickstart 11 / FR-008: disabling, and what is never throttled ---------------------------

    @Test
    void aDisabledThrottleRefusesNothing() {
        LoginThrottle off = new LoginThrottle(new LoginThrottle.Settings(
                false, 5, 20, Duration.ofMinutes(15)));

        for (int i = 0; i < 100; i++) {
            off.recordFailure(CALLER, T0);
        }

        assertThat(off.isRefused(CALLER, T0)).isFalse();
    }

    // --- FR-004 / research R4: the map does not grow without bound --------------------------------

    @Test
    void idleEntriesAreNotKeptForever() {
        // The per-caller key is chosen by whoever is calling, so an unbounded map would be a memory
        // exhaustion vector this feature introduced itself.
        //
        // The instance-wide tier is put out of reach here on purpose: with its usual threshold it
        // would trip after twenty failures and every later attempt would be refused rather than
        // recorded, so the map would stay small for a reason that has nothing to do with eviction
        // and the test would pass while proving nothing.
        LoginThrottle noCeiling = new LoginThrottle(new LoginThrottle.Settings(
                true, 5, Integer.MAX_VALUE, Duration.ofMinutes(15)));
        for (int caller = 0; caller < 1000; caller++) {
            noCeiling.recordFailure("caller-" + caller, T0);
        }
        assertThat(noCeiling.trackedKeys()).isGreaterThan(1000 / 2);

        noCeiling.recordFailure("someone", T0.plus(Duration.ofHours(2)));

        assertThat(noCeiling.trackedKeys())
                .as("entries idle beyond the retention period are evicted rather than accumulated")
                .isLessThan(10);
    }
}
