package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface UpdateSavingsGoalService {

    SavingsGoalStatusDto updateGoal(String id, String name, BigDecimal targetAmount, OffsetDateTime targetDate);
}
