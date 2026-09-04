package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar rules of auto-posting, under plain JUnit with no database and no clock.
 *
 * <p>This is where feature 023 is either right or wrong. Everything downstream — the scheduler, the
 * endpoint, the uniqueness constraint — is machinery around the answers this class gives.
 */
class OccurrenceScheduleTest {

    private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);

    private List<LocalDate> due(RecurringFrequency frequency, LocalDate lastReal, LocalDate today) {
        return OccurrenceSchedule.dueOccurrences(frequency, lastReal, LONG_AGO, today);
    }

    @Nested
    class WhatIsDue {

        @Test
        void theNextOccurrenceIsDueOnItsDate() {
            assertThat(due(RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)))
                    .containsExactly(LocalDate.of(2026, 3, 1));
        }

        @Test
        void nothingIsDueBeforeItsDate() {
            assertThat(due(RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                    .isEmpty();
        }

        @Test
        void aFutureOccurrenceIsNeverPosted() {
            // Posting ahead of time would put money in the ledger that has not moved.
            assertThat(due(RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)))
                    .doesNotContain(LocalDate.of(2026, 4, 1));
        }

        @Test
        void theAnchorItselfIsNotReposted() {
            assertThat(due(RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)))
                    .isEmpty();
        }
    }

    @Nested
    class DowntimeCatchUp {

        @Test
        void threeWeeksOffPostsExactlyTheThreeMissedWeeks() {
            // quickstart scenario 3. Two failure modes this catches: posting only the most recent
            // missed occurrence (a naive "is it due today?" check), and posting one per day of
            // downtime (a naive catch-up loop).
            List<LocalDate> occurrences = due(RecurringFrequency.WEEKLY,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 25));

            assertThat(occurrences).containsExactly(
                    LocalDate.of(2026, 3, 8),
                    LocalDate.of(2026, 3, 15),
                    LocalDate.of(2026, 3, 22));
        }

        @Test
        void aYearOffPostsTwelveMonthlyOccurrences() {
            assertThat(due(RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
                    .hasSize(12)
                    .first().isEqualTo(LocalDate.of(2026, 2, 1));
        }

        @Test
        void recomputingTheSameDayGivesTheSameAnswer() {
            // There is no "already ran today" flag; the same due set is produced every run and the
            // database refuses the repeats. That only works if this is deterministic.
            List<LocalDate> first = due(RecurringFrequency.WEEKLY,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 25));
            List<LocalDate> second = due(RecurringFrequency.WEEKLY,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 25));

            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    class ConfirmationIsNotRetroactive {

        @Test
        void nothingBeforeTheConfirmationDateIsEverPosted() {
            // quickstart scenario 7, and the worst thing this feature could do: a series detected
            // from two-year-old history must not fabricate two years of rent when confirmed.
            List<LocalDate> occurrences = OccurrenceSchedule.dueOccurrences(
                    RecurringFrequency.MONTHLY,
                    LocalDate.of(2024, 3, 1),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 4));

            assertThat(occurrences).containsExactly(LocalDate.of(2026, 9, 1));
        }

        @Test
        void aSeriesConfirmedAfterEveryDueDatePostsNothing() {
            assertThat(OccurrenceSchedule.dueOccurrences(
                    RecurringFrequency.MONTHLY,
                    LocalDate.of(2024, 3, 1),
                    LocalDate.of(2026, 12, 1),
                    LocalDate.of(2026, 9, 4)))
                    .isEmpty();
        }

        @Test
        void anOccurrenceExactlyOnTheConfirmationDateIsPosted() {
            // The bound is inclusive: confirming on the day rent falls due should not skip it.
            assertThat(OccurrenceSchedule.dueOccurrences(
                    RecurringFrequency.MONTHLY,
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 1)))
                    .containsExactly(LocalDate.of(2026, 9, 1));
        }
    }

    @Nested
    class MonthEnds {

        @Test
        void aMonthlySeriesOnThe31stResolvesFebruaryToItsLastDay() {
            // FR-017: a definite date, never a skip and never an exception.
            assertThat(OccurrenceSchedule.next(RecurringFrequency.MONTHLY, LocalDate.of(2026, 1, 31)))
                    .isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void aLeapYearFebruaryTakesThe29th() {
            assertThat(OccurrenceSchedule.next(RecurringFrequency.MONTHLY, LocalDate.of(2028, 1, 31)))
                    .isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        void a29FebruaryAnnualSeriesResolvesInANonLeapYear() {
            assertThat(OccurrenceSchedule.next(RecurringFrequency.YEARLY, LocalDate.of(2028, 2, 29)))
                    .isEqualTo(LocalDate.of(2029, 2, 28));
        }

        @Test
        void afterClampingItDoesNotReturnToThe31st() {
            // Deliberate: the next date is derived from the previous occurrence and nothing else.
            // Remembering "the 31st" would mean carrying a nominal day-of-month beside the ledger —
            // invisible state that can disagree with the transactions actually recorded. The drift
            // this causes stops at the first real occurrence, which re-anchors the series.
            assertThat(OccurrenceSchedule.next(RecurringFrequency.MONTHLY, LocalDate.of(2026, 2, 28)))
                    .isEqualTo(LocalDate.of(2026, 3, 28));
        }

        @Test
        void steppingNeverThrowsAcrossAWholeYearOfMonthEnds() {
            LocalDate date = LocalDate.of(2026, 1, 31);
            for (int i = 0; i < 12; i++) {
                date = OccurrenceSchedule.next(RecurringFrequency.MONTHLY, date);
                assertThat(date).isNotNull();
            }
        }
    }

    @Nested
    class Robustness {

        @Test
        void aSeriesWithNoAnchorProducesNothing() {
            // A series with no real occurrence has no date to step from — and no amount or account
            // either. It must be skipped, not guessed at.
            assertThat(OccurrenceSchedule.dueOccurrences(
                    RecurringFrequency.MONTHLY, null, LONG_AGO, LocalDate.of(2026, 3, 1)))
                    .isEmpty();
        }

        @Test
        void anAbsurdlyOldAnchorIsBoundedRatherThanFillingTheLedger() {
            // Daily cadence anchored in the year 1900: without a stop this would try to write
            // ~45,000 transactions. Reaching the cap means the inputs are wrong, and stopping is the
            // right response.
            assertThat(due(RecurringFrequency.DAILY,
                    LocalDate.of(1900, 1, 1), LocalDate.of(2026, 3, 1)))
                    .hasSize(1000);
        }
    }
}
