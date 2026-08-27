package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RecurringMatchingTest {

    @Test
    void normalizeDescription_trimsAndLowercases() {
        assertEquals("netflix", RecurringMatching.normalizeDescription("  Netflix  "));
        assertEquals("netflix subscription", RecurringMatching.normalizeDescription("Netflix Subscription"));
    }

    @Test
    void normalizeDescription_nullStaysNull() {
        assertNull(RecurringMatching.normalizeDescription(null));
    }

    @Test
    void isWithinCadenceTolerance_monthly_withinToleranceIsTrue() {
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.MONTHLY, Duration.ofDays(30)));
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.MONTHLY, Duration.ofDays(27)));
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.MONTHLY, Duration.ofDays(33)));
    }

    @Test
    void isWithinCadenceTolerance_monthly_outsideToleranceIsFalse() {
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.MONTHLY, Duration.ofDays(20)));
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.MONTHLY, Duration.ofDays(40)));
    }

    @Test
    void isWithinCadenceTolerance_weekly_withinAndOutsideTolerance() {
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.WEEKLY, Duration.ofDays(7)));
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.WEEKLY, Duration.ofDays(9)));
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.WEEKLY, Duration.ofDays(3)));
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.WEEKLY, Duration.ofDays(15)));
    }

    @Test
    void isWithinCadenceTolerance_daily_withinAndOutsideTolerance() {
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.DAILY, Duration.ofDays(1)));
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.DAILY, Duration.ofDays(5)));
    }

    @Test
    void isWithinCadenceTolerance_yearly_withinAndOutsideTolerance() {
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.YEARLY, Duration.ofDays(365)));
        assertTrue(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.YEARLY, Duration.ofDays(358)));
        assertFalse(RecurringMatching.isWithinCadenceTolerance(RecurringFrequency.YEARLY, Duration.ofDays(300)));
    }

    @Test
    void isWithinAmountTolerance_withinPercentThreshold_isTrue() {
        // 4% of 100.00 = 4.00, within the 5% tolerance
        assertTrue(RecurringMatching.isWithinAmountTolerance(new BigDecimal("100.00"), new BigDecimal("104.00")));
    }

    @Test
    void isWithinAmountTolerance_beyondPercentThreshold_isFalse() {
        // 10% of 100.00 = 10.00, beyond the 5% tolerance
        assertFalse(RecurringMatching.isWithinAmountTolerance(new BigDecimal("100.00"), new BigDecimal("110.00")));
    }

    @Test
    void isWithinAmountTolerance_smallAmounts_useFixedFloorNotPercent() {
        // 5% of 10.00 is only 0.50, but the fixed €2.00 floor applies instead
        assertTrue(RecurringMatching.isWithinAmountTolerance(new BigDecimal("10.00"), new BigDecimal("11.50")));
        assertFalse(RecurringMatching.isWithinAmountTolerance(new BigDecimal("10.00"), new BigDecimal("13.00")));
    }

    @Test
    void predictNextDate_monthly_addsOneMonth() {
        OffsetDateTime last = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(OffsetDateTime.of(2026, 2, 15, 10, 0, 0, 0, ZoneOffset.UTC),
                RecurringMatching.predictNextDate(last, RecurringFrequency.MONTHLY));
    }

    @Test
    void predictNextDate_yearly_addsOneYear() {
        OffsetDateTime last = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(OffsetDateTime.of(2027, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC),
                RecurringMatching.predictNextDate(last, RecurringFrequency.YEARLY));
    }

    @Test
    void predictNextDate_weekly_addsSevenDays() {
        OffsetDateTime last = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(OffsetDateTime.of(2026, 1, 22, 10, 0, 0, 0, ZoneOffset.UTC),
                RecurringMatching.predictNextDate(last, RecurringFrequency.WEEKLY));
    }

    @Test
    void predictNextDate_daily_addsOneDay() {
        OffsetDateTime last = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(OffsetDateTime.of(2026, 1, 16, 10, 0, 0, 0, ZoneOffset.UTC),
                RecurringMatching.predictNextDate(last, RecurringFrequency.DAILY));
    }
}
