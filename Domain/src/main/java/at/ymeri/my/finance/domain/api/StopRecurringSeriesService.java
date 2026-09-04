package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;

/**
 * Ends a confirmed series' auto-posting without losing what was detected.
 */
public interface StopRecurringSeriesService {

    /**
     * Moves a {@code CONFIRMED} series to {@code STOPPED}.
     *
     * <p>Transactions already posted are left exactly as they are, and the series keeps its
     * detection history and stays listed — stopping says the series was real and has ended, which is
     * a different statement from dismissing it as a bad detection.
     *
     * @throws java.util.NoSuchElementException if no such series exists
     * @throws IllegalStateException            if the series is not currently confirmed
     */
    RecurringSeriesDto stop(String id);
}
