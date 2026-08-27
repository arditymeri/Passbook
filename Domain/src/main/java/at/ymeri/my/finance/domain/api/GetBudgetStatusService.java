package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.budget.BudgetStatusResult;

public interface GetBudgetStatusService {

    BudgetStatusResult getBudgetStatus(int year, int month);
}
