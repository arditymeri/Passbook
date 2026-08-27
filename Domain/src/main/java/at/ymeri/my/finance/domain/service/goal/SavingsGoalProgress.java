package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure computation of a goal's progress from its target and its linked account's current
 * balance — nothing here is stored (Constitution Principle III). Built incrementally: this phase
 * covers percent complete, remaining amount, and achieved status; pace status is added later.
 */
public final class SavingsGoalProgress {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SavingsGoalProgress() {
    }

    public static SavingsGoalStatusDto of(SavingsGoalDto goal, BigDecimal savedAmount) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal saved = savedAmount != null ? savedAmount : BigDecimal.ZERO;

        BigDecimal rawFraction = saved.divide(target, 10, RoundingMode.HALF_EVEN);
        BigDecimal percentComplete = rawFraction.multiply(HUNDRED)
                .max(BigDecimal.ZERO)
                .min(HUNDRED)
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal remainingAmount = target.subtract(saved).max(BigDecimal.ZERO);
        boolean achieved = saved.compareTo(target) >= 0;

        SavingsGoalStatusDto status = new SavingsGoalStatusDto();
        status.setId(goal.getId());
        status.setName(goal.getName());
        status.setTargetAmount(target);
        status.setTargetDate(goal.getTargetDate());
        status.setAccountId(goal.getAccountId());
        status.setCreatedAt(goal.getCreatedAt());
        status.setSavedAmount(saved);
        status.setPercentComplete(percentComplete);
        status.setRemainingAmount(remainingAmount);
        status.setAchieved(achieved);
        status.setPaceStatus(null);
        return status;
    }
}
