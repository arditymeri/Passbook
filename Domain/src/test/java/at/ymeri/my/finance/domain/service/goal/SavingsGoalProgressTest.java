package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.data.goal.PaceStatus;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavingsGoalProgressTest {

    @Test
    void of_savedBelowTarget_computesPercentCompleteAndRemaining() {
        SavingsGoalDto goal = goal(new BigDecimal("1000"), null);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("250"));

        assertEquals(0, new BigDecimal("25.00").compareTo(status.getPercentComplete()));
        assertEquals(0, new BigDecimal("750").compareTo(status.getRemainingAmount()));
        assertFalse(status.isAchieved());
    }

    @Test
    void of_savedAtTarget_isAchievedWithZeroRemaining() {
        SavingsGoalDto goal = goal(new BigDecimal("1000"), null);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("1000"));

        assertEquals(0, new BigDecimal("100.00").compareTo(status.getPercentComplete()));
        assertEquals(0, BigDecimal.ZERO.compareTo(status.getRemainingAmount()));
        assertTrue(status.isAchieved());
    }

    @Test
    void of_savedAboveTarget_percentCappedAtHundredAndRemainingFlooredAtZero() {
        SavingsGoalDto goal = goal(new BigDecimal("1000"), null);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("1500"));

        assertEquals(0, new BigDecimal("100.00").compareTo(status.getPercentComplete()));
        assertEquals(0, BigDecimal.ZERO.compareTo(status.getRemainingAmount()));
        assertTrue(status.isAchieved());
    }

    @Test
    void of_savedNegative_percentFlooredAtZero() {
        SavingsGoalDto goal = goal(new BigDecimal("1000"), null);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("-50"));

        assertEquals(0, BigDecimal.ZERO.compareTo(status.getPercentComplete()));
        assertEquals(0, new BigDecimal("1050").compareTo(status.getRemainingAmount()));
        assertFalse(status.isAchieved());
    }

    @Test
    void of_noTargetDate_paceStatusIsNull() {
        SavingsGoalDto goal = goal(new BigDecimal("1000"), null, OffsetDateTime.now().minusMonths(1));

        assertNull(SavingsGoalProgress.of(goal, new BigDecimal("100")).getPaceStatus());
    }

    @Test
    void of_progressAtOrAheadOfStraightLinePace_isOnPace() {
        // Created 30 days ago, target date 120 days from creation: exactly 25% of time elapsed.
        // 30% saved is at/ahead of the 25% expected fraction.
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(30);
        SavingsGoalDto goal = goal(new BigDecimal("1000"), createdAt.plusDays(120), createdAt);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("300"));

        assertEquals(PaceStatus.ON_PACE, status.getPaceStatus());
    }

    @Test
    void of_progressBehindStraightLinePace_isBehindPace() {
        // Same timeline as above (25% elapsed), but only 5% saved — behind pace.
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(30);
        SavingsGoalDto goal = goal(new BigDecimal("1000"), createdAt.plusDays(120), createdAt);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("50"));

        assertEquals(PaceStatus.BEHIND_PACE, status.getPaceStatus());
    }

    @Test
    void of_targetDatePassedAndNotAchieved_isOverdue() {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(90);
        SavingsGoalDto goal = goal(new BigDecimal("1000"), createdAt.plusDays(30), createdAt);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("100"));

        assertEquals(PaceStatus.OVERDUE, status.getPaceStatus());
    }

    @Test
    void of_targetDatePassedButAchieved_paceStatusIsNullNotOverdue() {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(90);
        SavingsGoalDto goal = goal(new BigDecimal("1000"), createdAt.plusDays(30), createdAt);

        SavingsGoalStatusDto status = SavingsGoalProgress.of(goal, new BigDecimal("1000"));

        assertTrue(status.isAchieved());
        assertNull(status.getPaceStatus());
    }

    private SavingsGoalDto goal(BigDecimal targetAmount, OffsetDateTime targetDate) {
        return goal(targetAmount, targetDate, OffsetDateTime.now().minusMonths(1));
    }

    private SavingsGoalDto goal(BigDecimal targetAmount, OffsetDateTime targetDate, OffsetDateTime createdAt) {
        SavingsGoalDto dto = new SavingsGoalDto();
        dto.setId("goal-1");
        dto.setName("Vacation Fund");
        dto.setTargetAmount(targetAmount);
        dto.setTargetDate(targetDate);
        dto.setAccountId("account-1");
        dto.setCreatedAt(createdAt);
        return dto;
    }
}
