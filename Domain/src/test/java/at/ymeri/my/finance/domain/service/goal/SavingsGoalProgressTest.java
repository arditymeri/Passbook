package at.ymeri.my.finance.domain.service.goal;

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
    void of_noPaceLogicYet_paceStatusIsAlwaysNull() {
        SavingsGoalDto goalWithDate = goal(new BigDecimal("1000"), OffsetDateTime.now().plusMonths(3));
        SavingsGoalDto goalWithoutDate = goal(new BigDecimal("1000"), null);

        assertNull(SavingsGoalProgress.of(goalWithDate, new BigDecimal("100")).getPaceStatus());
        assertNull(SavingsGoalProgress.of(goalWithoutDate, new BigDecimal("100")).getPaceStatus());
    }

    private SavingsGoalDto goal(BigDecimal targetAmount, OffsetDateTime targetDate) {
        SavingsGoalDto dto = new SavingsGoalDto();
        dto.setId("goal-1");
        dto.setName("Vacation Fund");
        dto.setTargetAmount(targetAmount);
        dto.setTargetDate(targetDate);
        dto.setAccountId("account-1");
        dto.setCreatedAt(OffsetDateTime.now().minusMonths(1));
        return dto;
    }
}
