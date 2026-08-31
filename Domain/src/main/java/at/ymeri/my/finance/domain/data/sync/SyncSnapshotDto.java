package at.ymeri.my.finance.domain.data.sync;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A full-state export of one device's data (spec User Story 1), or the payload an import merges
 * in (User Stories 2-4). Bills/incomes include correction-replacement and reversal rows exactly
 * as stored — a faithful snapshot must include every row, not just the currently-visible ones.
 */
@Data
public class SyncSnapshotDto {

    private int schemaVersion;
    private OffsetDateTime exportedAt;
    private List<AccountDto> accounts;
    private List<CategoryDto> categories;
    private List<BudgetDto> budgets;
    private List<RecurringSeriesDto> recurringSeries;
    private List<BillDto> bills;
    private List<IncomeDto> incomes;
    private List<SavingsGoalDto> savingsGoals;
}
