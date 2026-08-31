package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.sync.EntityMergePlan;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.data.sync.MergeUpdate;
import at.ymeri.my.finance.domain.spi.UnitOfWork;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.UpdateAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.UpdateCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.AddSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.UpdateSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.AddRecurringSeriesPersistencePort;
import org.springframework.stereotype.Service;

/**
 * Writes an already-computed {@link MergePlanDto} (from {@link ComputeMergePlanService}) through
 * each entity's persistence port directly — never through the validating {@code Add*Service}/
 * {@code Update*Service} layer, since re-running origin-device validation (e.g. a duplicate-name
 * check) against data that already passed it on the origin device risks wrongly rejecting a
 * legitimate incoming entity (research.md R5). Applies entity types in the same dependency order
 * {@code ComputeMergePlanService} used to plan them (research.md R6), inside one transaction, and
 * only ever inserts or updates — never deletes (FR-011).
 */
@Service
public class ApplyMergePlanService {

    private final UnitOfWork unitOfWork;
    private final AddAccountPersistencePort addAccountPersistencePort;
    private final UpdateAccountPersistencePort updateAccountPersistencePort;
    private final AddCategoryPersistencePort addCategoryPersistencePort;
    private final UpdateCategoryPersistencePort updateCategoryPersistencePort;
    private final SetBudgetPersistencePort setBudgetPersistencePort;
    private final AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort;
    private final AddBillPersistencePort addBillPersistencePort;
    private final AddIncomePersistencePort addIncomePersistencePort;
    private final AddSavingsGoalPersistencePort addSavingsGoalPersistencePort;
    private final UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort;

    public ApplyMergePlanService(UnitOfWork unitOfWork,
                                  AddAccountPersistencePort addAccountPersistencePort,
                                  UpdateAccountPersistencePort updateAccountPersistencePort,
                                  AddCategoryPersistencePort addCategoryPersistencePort,
                                  UpdateCategoryPersistencePort updateCategoryPersistencePort,
                                  SetBudgetPersistencePort setBudgetPersistencePort,
                                  AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort,
                                  AddBillPersistencePort addBillPersistencePort,
                                  AddIncomePersistencePort addIncomePersistencePort,
                                  AddSavingsGoalPersistencePort addSavingsGoalPersistencePort,
                                  UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort) {
        this.unitOfWork = unitOfWork;
        this.addAccountPersistencePort = addAccountPersistencePort;
        this.updateAccountPersistencePort = updateAccountPersistencePort;
        this.addCategoryPersistencePort = addCategoryPersistencePort;
        this.updateCategoryPersistencePort = updateCategoryPersistencePort;
        this.setBudgetPersistencePort = setBudgetPersistencePort;
        this.addRecurringSeriesPersistencePort = addRecurringSeriesPersistencePort;
        this.addBillPersistencePort = addBillPersistencePort;
        this.addIncomePersistencePort = addIncomePersistencePort;
        this.addSavingsGoalPersistencePort = addSavingsGoalPersistencePort;
        this.updateSavingsGoalPersistencePort = updateSavingsGoalPersistencePort;
    }

    public void apply(MergePlanDto plan) {
        unitOfWork.runInTransaction(() -> {
            applyAccounts(plan.getAccounts());
            applyCategories(plan.getCategories());
            applyBudgets(plan.getBudgets());
            applyRecurringSeries(plan.getRecurringSeries());
            applyBills(plan.getBills());
            applyIncomes(plan.getIncomes());
            applySavingsGoals(plan.getSavingsGoals());
        });
    }

    private void applyAccounts(EntityMergePlan<AccountDto> accountPlan) {
        for (AccountDto account : accountPlan.getToInsert()) {
            addAccountPersistencePort.addAccount(account);
        }
        for (MergeUpdate<AccountDto> update : accountPlan.getToUpdate()) {
            updateAccountPersistencePort.updateAccount(update.localId(), update.incoming());
        }
    }

    private void applyCategories(EntityMergePlan<CategoryDto> categoryPlan) {
        for (CategoryDto category : categoryPlan.getToInsert()) {
            addCategoryPersistencePort.addCategory(category);
        }
        for (MergeUpdate<CategoryDto> update : categoryPlan.getToUpdate()) {
            updateCategoryPersistencePort.updateCategory(update.localId(), update.incoming());
        }
    }

    private void applyBudgets(EntityMergePlan<BudgetDto> budgetPlan) {
        for (BudgetDto budget : budgetPlan.getToInsert()) {
            setBudgetPersistencePort.upsert(budget);
        }
        for (MergeUpdate<BudgetDto> update : budgetPlan.getToUpdate()) {
            // A natural-key match can carry a different incoming id than the local row it matched
            // (data-model.md's matching rule) — the local id is the one other rows may already
            // reference, so it must win before this goes through upsert()'s save()-as-merge path.
            BudgetDto toWrite = update.incoming();
            toWrite.setId(update.localId());
            setBudgetPersistencePort.upsert(toWrite);
        }
    }

    private void applyRecurringSeries(EntityMergePlan<RecurringSeriesDto> seriesPlan) {
        for (RecurringSeriesDto series : seriesPlan.getToInsert()) {
            addRecurringSeriesPersistencePort.add(series);
        }
        for (MergeUpdate<RecurringSeriesDto> update : seriesPlan.getToUpdate()) {
            RecurringSeriesDto toWrite = update.incoming();
            toWrite.setId(update.localId());
            addRecurringSeriesPersistencePort.add(toWrite);
        }
    }

    private void applyBills(EntityMergePlan<BillDto> billPlan) {
        for (BillDto bill : billPlan.getToInsert()) {
            addBillPersistencePort.addBill(bill);
        }
        for (MergeUpdate<BillDto> update : billPlan.getToUpdate()) {
            // Bills match by id only (Principle II), so localId always equals incoming's own id —
            // this is purely a necessity-tag change carried on an otherwise-identical row, applied
            // via the same insert-or-merge-by-id path every other entity uses, per
            // UpdateBillNecessityTagPostgresAdapter's javadoc.
            addBillPersistencePort.addBill(update.incoming());
        }
    }

    private void applyIncomes(EntityMergePlan<IncomeDto> incomePlan) {
        for (IncomeDto income : incomePlan.getToInsert()) {
            addIncomePersistencePort.addIncome(income);
        }
        // Incomes never produce toUpdate entries (ComputeMergePlanService: no per-device-mutable
        // field exists on an income), so nothing to apply here beyond inserts.
    }

    private void applySavingsGoals(EntityMergePlan<SavingsGoalDto> goalPlan) {
        for (SavingsGoalDto goal : goalPlan.getToInsert()) {
            addSavingsGoalPersistencePort.add(goal);
        }
        for (MergeUpdate<SavingsGoalDto> update : goalPlan.getToUpdate()) {
            updateSavingsGoalPersistencePort.update(update.localId(), update.incoming());
        }
    }
}
