package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.data.goal.PaceStatus;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Pure computation of a goal's progress from its target and its linked account's current
 * balance — nothing here is stored (Constitution Principle III). Pace status compares actual
 * progress to the straight-line progress expected between the goal's creation date and its
 * target date (research.md); achieved always takes precedence over overdue.
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
        status.setPaceStatus(paceStatus(goal, saved, target, achieved));
        return status;
    }

    private static PaceStatus paceStatus(SavingsGoalDto goal, BigDecimal saved, BigDecimal target, boolean achieved) {
        OffsetDateTime targetDate = goal.getTargetDate();
        if (targetDate == null || achieved) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(targetDate)) {
            return PaceStatus.OVERDUE;
        }

        OffsetDateTime createdAt = goal.getCreatedAt();
        long totalMillis = Duration.between(createdAt, targetDate).toMillis();
        long elapsedMillis = Duration.between(createdAt, now).toMillis();
        BigDecimal expectedFraction = totalMillis <= 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(elapsedMillis)
                        .divide(BigDecimal.valueOf(totalMillis), 10, RoundingMode.HALF_EVEN)
                        .max(BigDecimal.ZERO)
                        .min(BigDecimal.ONE);
        BigDecimal actualFraction = saved.divide(target, 10, RoundingMode.HALF_EVEN)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        return actualFraction.compareTo(expectedFraction) >= 0 ? PaceStatus.ON_PACE : PaceStatus.BEHIND_PACE;
    }
}
