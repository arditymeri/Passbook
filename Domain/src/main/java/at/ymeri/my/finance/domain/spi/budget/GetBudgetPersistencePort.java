package at.ymeri.my.finance.domain.spi.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetBudgetPersistencePort {

    List<BudgetDto> findByYearAndMonth(int year, int month);

    Optional<BudgetDto> findByCategoryIdAndYearAndMonth(String categoryId, int year, int month);

    Optional<BudgetDto> findById(UUID id);

    boolean existsById(UUID id);
}
