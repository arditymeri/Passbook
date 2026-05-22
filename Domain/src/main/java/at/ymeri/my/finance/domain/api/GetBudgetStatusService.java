package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.budget.BudgetStatusDto;

import java.util.List;

public interface GetBudgetStatusService {

    List<BudgetStatusDto> getBudgetStatus(int year, int month);
}
