package at.ymeri.my.finance.domain.spi.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;

public interface UpdateSavingsGoalPersistencePort {

    SavingsGoalDto update(String id, SavingsGoalDto goal);
}
