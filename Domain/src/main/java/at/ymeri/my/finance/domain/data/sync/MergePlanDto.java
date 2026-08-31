package at.ymeri.my.finance.domain.data.sync;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import lombok.Data;

import java.util.List;

/**
 * What importing a {@link SyncSnapshotDto} would do, computed by {@code ComputeMergePlanService}
 * without writing anything. {@code PreviewSyncImportServiceImpl} summarizes this and stops;
 * {@code ApplySyncImportServiceImpl} summarizes it too, then hands it to
 * {@code ApplyMergePlanService} to actually persist.
 */
@Data
public class MergePlanDto {

    private EntityMergePlan<AccountDto> accounts;
    private EntityMergePlan<CategoryDto> categories;
    private EntityMergePlan<BudgetDto> budgets;
    private EntityMergePlan<RecurringSeriesDto> recurringSeries;
    private EntityMergePlan<BillDto> bills;
    private EntityMergePlan<IncomeDto> incomes;
    private EntityMergePlan<SavingsGoalDto> savingsGoals;
    private List<CorrectionConflict> billCorrectionConflicts;
    private List<CorrectionConflict> incomeCorrectionConflicts;

    public int totalCorrectionConflicts() {
        return billCorrectionConflicts.size() + incomeCorrectionConflicts.size();
    }
}
