package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which occurrences of a recurring series are currently due.
 *
 * <p>Pure calendar arithmetic: no I/O, no clock, no database. Everything interesting about
 * auto-posting — three weeks of downtime, a monthly series anchored on the 31st, the rule that
 * confirming a series must not fabricate its past — lives here and is testable in milliseconds.
 *
 * <p><strong>Stepping uses calendar arithmetic, not
 * {@code RecurringMatching.nominalInterval}.</strong> That method maps {@code MONTHLY} to
 * {@code Duration.ofDays(30)}, which is right for asking "is this gap consistent with a monthly
 * cadence?" and wrong for asking "when is the next one?": stepping rent due on the 1st by 30 days
 * lands on the 2nd, then the 3rd, and walks away from the date it is actually paid. The two
 * questions need different arithmetic and the resemblance between them is a trap.
 *
 * <p><strong>Why there is no "since the last run" parameter.</strong> The due set is derived from
 * the ledger every time rather than from a record of progress. A run an hour ago and a run after
 * three weeks of downtime take the identical path; the difference shows up only in how many dates
 * come back, and re-posting an occurrence that already exists is refused by the database. Tracking
 * progress instead would mean state that can be written but not committed, or reset by a restore
 * from backup — each one silently skipping or repeating an operator's rent.
 */
public final class OccurrenceSchedule {

    /**
     * A safety stop. Reaching it means the inputs are wrong — a series anchored decades ago, or a
     * cadence that failed to advance — and the right response is to stop rather than to fill a
     * ledger with thousands of invented transactions.
     */
    private static final int MAX_OCCURRENCES_PER_RUN = 1000;

    private OccurrenceSchedule() {
    }

    /**
     * Every occurrence date currently due for a series, oldest first.
     *
     * @param frequency        the series' cadence
     * @param lastRealOccurrence the date of the most recent occurrence that a person entered or a
     *                           bank reported. Never one this app posted — a prediction is not
     *                           evidence, and anchoring on one makes the app derive next month from
     *                           its own guess.
     * @param confirmedOn      when the operator confirmed the series. Nothing before this date is
     *                           ever returned: confirming says "expect this from now on", not
     *                           "invent the past", and a series detected from two-year-old history
     *                           must not fabricate two years of rent the moment it is confirmed.
     * @param today            the date to post up to, inclusive. A parameter rather than a clock
     *                           read, which is what makes every scenario here testable.
     */
    public static List<LocalDate> dueOccurrences(RecurringFrequency frequency,
                                                 LocalDate lastRealOccurrence,
                                                 LocalDate confirmedOn,
                                                 LocalDate today) {
        if (frequency == null || lastRealOccurrence == null || today == null) {
            return List.of();
        }

        List<LocalDate> due = new ArrayList<>();
        LocalDate candidate = next(frequency, lastRealOccurrence);

        while (!candidate.isAfter(today) && due.size() < MAX_OCCURRENCES_PER_RUN) {
            if (confirmedOn == null || !candidate.isBefore(confirmedOn)) {
                due.add(candidate);
            }
            candidate = next(frequency, candidate);
        }
        return List.copyOf(due);
    }

    /**
     * The occurrence after this one.
     *
     * <p>Month and year steps clamp to the last valid day when the nominal day does not exist —
     * 31 January stepped monthly gives 28 or 29 February — which is {@code java.time}'s own
     * behaviour and satisfies FR-017 without a rule of our own. Note that it does not "remember"
     * the 31st: February to March gives the 28th to the 28th. That is the deterministic answer, and
     * a series drifting to the 28th is visible and correctable, whereas the alternative (carrying a
     * nominal day-of-month separately) is invisible state that can disagree with the ledger.
     */
    static LocalDate next(RecurringFrequency frequency, LocalDate from) {
        return switch (frequency) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case YEARLY -> from.plusYears(1);
        };
    }
}
