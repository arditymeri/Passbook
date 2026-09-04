package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.recurring.PostingRunResult;

import java.time.LocalDate;

/**
 * Records the transactions that confirmed recurring series are currently due to produce.
 */
public interface PostDueOccurrencesService {

    /**
     * Posts every occurrence due on or before {@code today} that is not already recorded.
     *
     * <p><strong>{@code today} is a parameter rather than a clock read on purpose.</strong> All of
     * this feature's interesting behaviour is calendar behaviour — catching up after downtime, the
     * rule that confirmation is not retroactive, a monthly series anchored on the 31st — and none of
     * it is testable if the date is read from the system clock inside the implementation.
     *
     * <p>Safe to call at any time and any number of times: an occurrence already recorded is never
     * recorded again, which is the same guarantee that makes catch-up safe.
     */
    PostingRunResult postDueOccurrences(LocalDate today);
}
