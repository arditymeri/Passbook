package at.ymeri.my.finance.domain.spi.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;

public interface AddSavingsGoalPersistencePort {

    SavingsGoalDto add(SavingsGoalDto goal);
}
