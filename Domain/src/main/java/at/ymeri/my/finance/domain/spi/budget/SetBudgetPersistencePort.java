package at.ymeri.my.finance.domain.spi.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;

public interface SetBudgetPersistencePort {

    BudgetDto upsert(BudgetDto budgetDto);
}
