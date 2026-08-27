package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface AddSavingsGoalService {

    SavingsGoalStatusDto addGoal(String name, BigDecimal targetAmount, OffsetDateTime targetDate, String accountId);
}
