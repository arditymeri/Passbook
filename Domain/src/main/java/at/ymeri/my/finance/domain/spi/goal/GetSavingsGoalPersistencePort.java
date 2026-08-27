package at.ymeri.my.finance.domain.spi.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;

import java.util.List;
import java.util.Optional;

public interface GetSavingsGoalPersistencePort {

    List<SavingsGoalDto> getAll();

    Optional<SavingsGoalDto> findById(String id);

    Optional<SavingsGoalDto> findByAccountId(String accountId);
}
