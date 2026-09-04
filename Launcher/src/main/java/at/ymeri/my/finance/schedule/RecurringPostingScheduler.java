package at.ymeri.my.finance.schedule;

import at.ymeri.my.finance.domain.api.PostDueOccurrencesService;
import at.ymeri.my.finance.domain.data.recurring.PostingRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Runs auto-posting daily, and once shortly after startup.
 *
 * <p><strong>The startup run is not a separate catch-up mechanism.</strong> Posting derives its work
 * from the ledger every time rather than from a record of where it left off, so a run after three
 * weeks of downtime and a run an hour later take the identical path — the first simply finds more
 * occurrences due. The startup run exists only so an operator who switches their instance on after a
 * break does not wait until the next daily slot to see their history filled in.
 *
 * <p><strong>Disabled in tests</strong> via {@code app.recurring.auto-post.enabled}. Every
 * integration test boots the full application against a shared database; a live schedule would post
 * against it mid-suite and surface as unrelated features failing balance assertions, which is close
 * to undiagnosable from the symptom.
 *
 * <p>Lives here beside {@code DemoDataSeeder} — Launcher is where this project already puts drivers
 * that are neither HTTP nor persistence.
 */
@Component
@ConditionalOnProperty(name = "app.recurring.auto-post.enabled", havingValue = "true", matchIfMissing = true)
public class RecurringPostingScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringPostingScheduler.class);

    private final PostDueOccurrencesService postDueOccurrencesService;

    public RecurringPostingScheduler(PostDueOccurrencesService postDueOccurrencesService) {
        this.postDueOccurrencesService = postDueOccurrencesService;
    }

    /**
     * Daily, plus once a minute after startup. The cadence only has to be finer than the shortest
     * supported series interval, which is daily; anything more frequent would just re-offer the same
     * occurrences for the database to refuse.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 86_400_000)
    public void postDueOccurrences() {
        try {
            PostingRunResult result = postDueOccurrencesService.postDueOccurrences(LocalDate.now());
            if (result.postedCount() > 0 || result.skippedSeriesCount() > 0) {
                log.info("Recurring auto-post: recorded {}, already present {}, series skipped for "
                                + "lack of history {}",
                        result.postedCount(), result.alreadyPostedCount(), result.skippedSeriesCount());
            }
        } catch (RuntimeException e) {
            // A failed run must not kill the schedule: the next one recomputes the same due set from
            // scratch, so a transient failure costs a day rather than a period.
            log.error("Recurring auto-post run failed; the next run will retry the same occurrences", e);
        }
    }
}
