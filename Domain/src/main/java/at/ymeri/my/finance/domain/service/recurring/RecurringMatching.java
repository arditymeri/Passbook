package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared matching primitives for recurring-series detection, prediction, and price-change
 * comparison — grouping, cadence tolerance, amount tolerance, and next-date prediction. Kept
 * pure/stateless so every recurring-related service composes the same rules consistently.
 */
public final class RecurringMatching {

    private static final BigDecimal AMOUNT_PERCENT_TOLERANCE = new BigDecimal("0.05");
    private static final BigDecimal AMOUNT_FLOOR_TOLERANCE = new BigDecimal("2.00");

    private RecurringMatching() {
    }

    public static String normalizeDescription(String description) {
        return description == null ? null : description.trim().toLowerCase();
    }

    /**
     * Whether the elapsed gap between two occurrence dates is consistent with the given cadence,
     * within a tolerance window scaled to that cadence's interval.
     */
    public static boolean isWithinCadenceTolerance(RecurringFrequency frequency, Duration gap) {
        Duration nominal = nominalInterval(frequency);
        Duration tolerance = toleranceFor(frequency);
        Duration lowerBound = nominal.minus(tolerance);
        Duration upperBound = nominal.plus(tolerance);
        return gap.compareTo(lowerBound) >= 0 && gap.compareTo(upperBound) <= 0;
    }

    /**
     * How many times a series of this frequency occurs per calendar month on average — used to
     * normalize a per-occurrence amount into a monthly-equivalent figure so series of different
     * frequencies can be ranked and summed together.
     */
    public static double occurrencesPerMonth(RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> 30.44;
            case WEEKLY -> 4.348;
            case MONTHLY -> 1;
            case YEARLY -> 1.0 / 12;
        };
    }

    private static Duration nominalInterval(RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> Duration.ofDays(1);
            case WEEKLY -> Duration.ofDays(7);
            case MONTHLY -> Duration.ofDays(30);
            case YEARLY -> Duration.ofDays(365);
        };
    }

    private static Duration toleranceFor(RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> Duration.ofDays(1);
            case WEEKLY -> Duration.ofDays(2);
            case MONTHLY -> Duration.ofDays(3);
            case YEARLY -> Duration.ofDays(10);
        };
    }

    /**
     * Whether {@code candidate} is close enough to {@code prior} to be "the same charge" rather
     * than a price change — within whichever is larger: 5% of the prior amount, or a fixed
     * €2.00 floor (so small amounts aren't held to an unrealistically tight percentage).
     */
    public static boolean isWithinAmountTolerance(BigDecimal prior, BigDecimal candidate) {
        BigDecimal difference = candidate.subtract(prior).abs();
        BigDecimal percentTolerance = prior.abs().multiply(AMOUNT_PERCENT_TOLERANCE);
        BigDecimal tolerance = percentTolerance.max(AMOUNT_FLOOR_TOLERANCE);
        return difference.compareTo(tolerance) <= 0;
    }

    /**
     * The next expected occurrence date for a series whose most recent occurrence was
     * {@code last}, using calendar-correct arithmetic (e.g. a monthly series lands on the same
     * day next month, not exactly 30 days later).
     */
    public static OffsetDateTime predictNextDate(OffsetDateTime last, RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> last.plusDays(1);
            case WEEKLY -> last.plusWeeks(1);
            case MONTHLY -> last.plusMonths(1);
            case YEARLY -> last.plusYears(1);
        };
    }

    /**
     * Every occurrence of a series expected to fall within a forecast window, walking
     * {@link #predictNextDate} forward one step at a time. The first predicted date is
     * {@code asOf} itself when the series is {@code overdue} (its previously predicted date has
     * already passed with nothing new recorded — treated as due "now" rather than at its stale
     * date), otherwise it is {@code predictNextDate(latestOccurrence, frequency)}; every
     * subsequent date is {@code predictNextDate} applied to the one before it. Stops as soon as a
     * predicted date exceeds {@code windowEnd} (exclusive); may return an empty list.
     */
    public static List<OffsetDateTime> predictOccurrencesWithinWindow(OffsetDateTime asOf,
                                                                        OffsetDateTime latestOccurrence,
                                                                        boolean overdue,
                                                                        RecurringFrequency frequency,
                                                                        OffsetDateTime windowEnd) {
        List<OffsetDateTime> occurrences = new ArrayList<>();
        OffsetDateTime next = overdue ? asOf : predictNextDate(latestOccurrence, frequency);
        while (!next.isAfter(windowEnd)) {
            occurrences.add(next);
            next = predictNextDate(next, frequency);
        }
        return occurrences;
    }
}
