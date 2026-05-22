package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetBudgetService {

    List<BudgetDto> getByYearAndMonth(int year, int month);

    Optional<BudgetDto> getById(UUID id);
}
