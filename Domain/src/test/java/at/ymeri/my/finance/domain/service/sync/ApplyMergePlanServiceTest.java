package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.sync.EntityMergePlan;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.spi.DirectUnitOfWork;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyMergePlanServiceTest {

    @Mock
    private AddAccountPersistencePort addAccountPersistencePort;
    @Mock
    private UpdateAccountPersistencePort updateAccountPersistencePort;
    @Mock
    private AddCategoryPersistencePort addCategoryPersistencePort;
    @Mock
    private UpdateCategoryPersistencePort updateCategoryPersistencePort;
    @Mock
    private SetBudgetPersistencePort setBudgetPersistencePort;
    @Mock
    private AddRecurringSeriesPersistencePort addRecurringSeriesPersistencePort;
    @Mock
    private AddBillPersistencePort addBillPersistencePort;
    @Mock
    private AddIncomePersistencePort addIncomePersistencePort;
    @Mock
    private AddSavingsGoalPersistencePort addSavingsGoalPersistencePort;
    @Mock
    private UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort;

    private ApplyMergePlanService service;

    @BeforeEach
    void setUp() {
        service = new ApplyMergePlanService(new DirectUnitOfWork(), addAccountPersistencePort,
                updateAccountPersistencePort, addCategoryPersistencePort, updateCategoryPersistencePort,
                setBudgetPersistencePort, addRecurringSeriesPersistencePort, addBillPersistencePort,
                addIncomePersistencePort, addSavingsGoalPersistencePort, updateSavingsGoalPersistencePort);
    }

    private <T> EntityMergePlan<T> emptyPlan() {
        EntityMergePlan<T> plan = new EntityMergePlan<>();
        plan.setToInsert(List.of());
        plan.setToUpdate(List.of());
        plan.setUnchangedCount(0);
        return plan;
    }

    private MergePlanDto emptyMergePlan() {
        MergePlanDto plan = new MergePlanDto();
        plan.setAccounts(emptyPlan());
        plan.setCategories(emptyPlan());
        plan.setBudgets(emptyPlan());
        plan.setRecurringSeries(emptyPlan());
        plan.setBills(emptyPlan());
        plan.setIncomes(emptyPlan());
        plan.setSavingsGoals(emptyPlan());
        plan.setBillCorrectionConflicts(List.of());
        plan.setIncomeCorrectionConflicts(List.of());
        return plan;
    }

    /**
     * {@link ApplyMergePlanService}'s constructor accepts only persistence ports (and
     * {@code UnitOfWork}) — no {@code Add*Service}/{@code Update*Service}, and no delete-capable
     * port of any kind. This is enforced at compile time by the class's own field types, which
     * this test pins down: a future edit that widened the constructor to accept a validating
     * service or a delete port would fail it immediately (FR-011's never-deletes guarantee,
     * research.md R5's "persistence ports only" rule).
     */
    @Test
    void constructorDependencies_areOnlyPersistencePortsOrUnitOfWork() {
        for (Field field : ApplyMergePlanService.class.getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            assertTrue(typeName.equals("UnitOfWork") || typeName.endsWith("PersistencePort"),
                    "Unexpected dependency type on ApplyMergePlanService: " + typeName);
            assertTrue(!typeName.toLowerCase().contains("delete"),
                    "ApplyMergePlanService must never depend on a delete-capable port: " + typeName);
        }
    }

    @Test
    void planWithOnlyInsertsAndUpdates_callsOnlyTheExpectedAddAndUpdateMethods() {
        AccountDto insertedAccount = account("acct-new");
        MergePlanDto plan = emptyMergePlan();
        plan.getAccounts().setToInsert(List.of(insertedAccount));

        CategoryDto insertedCategory = new CategoryDto();
        insertedCategory.setId("cat-new");
        plan.getCategories().setToInsert(List.of(insertedCategory));

        when(addAccountPersistencePort.addAccount(insertedAccount)).thenReturn(insertedAccount);
        when(addCategoryPersistencePort.addCategory(insertedCategory)).thenReturn(insertedCategory);

        service.apply(plan);

        verify(addAccountPersistencePort).addAccount(insertedAccount);
        verify(addCategoryPersistencePort).addCategory(insertedCategory);
        verifyNoMoreInteractions(addAccountPersistencePort, updateAccountPersistencePort,
                addCategoryPersistencePort, updateCategoryPersistencePort, setBudgetPersistencePort,
                addRecurringSeriesPersistencePort, addBillPersistencePort, addIncomePersistencePort,
                addSavingsGoalPersistencePort, updateSavingsGoalPersistencePort);
    }

    @Test
    void accountReferencedByABill_isAppliedBeforeThatBill_regardlessOfPlanOrdering() {
        AccountDto account = account("acct-1");
        BillDto bill = new BillDto();
        bill.setId("bill-1");
        bill.setAccountId("acct-1");
        bill.setAmount(BigDecimal.TEN);
        bill.setCurrency("EUR");

        MergePlanDto plan = emptyMergePlan();
        plan.getAccounts().setToInsert(List.of(account));
        plan.getBills().setToInsert(List.of(bill));

        lenient().when(addAccountPersistencePort.addAccount(account)).thenReturn(account);
        lenient().when(addBillPersistencePort.addBill(bill)).thenReturn(bill);

        service.apply(plan);

        InOrder order = inOrder(addAccountPersistencePort, addBillPersistencePort);
        order.verify(addAccountPersistencePort).addAccount(account);
        order.verify(addBillPersistencePort).addBill(bill);
    }

    @Test
    void toUpdateEntries_writeThroughUpdatePorts_notAddPorts() {
        CategoryDto incomingCategory = new CategoryDto();
        incomingCategory.setId("cat-remote");
        incomingCategory.setName("Utilities");

        SavingsGoalDto incomingGoal = new SavingsGoalDto();
        incomingGoal.setId("goal-remote");
        incomingGoal.setName("Vacation");

        MergePlanDto plan = emptyMergePlan();
        plan.getCategories().setToUpdate(List.of(new at.ymeri.my.finance.domain.data.sync.MergeUpdate<>("cat-local", incomingCategory)));
        plan.getSavingsGoals().setToUpdate(List.of(new at.ymeri.my.finance.domain.data.sync.MergeUpdate<>("goal-local", incomingGoal)));

        lenient().when(updateCategoryPersistencePort.updateCategory("cat-local", incomingCategory)).thenReturn(incomingCategory);
        lenient().when(updateSavingsGoalPersistencePort.update("goal-local", incomingGoal)).thenReturn(incomingGoal);

        service.apply(plan);

        verify(updateCategoryPersistencePort).updateCategory("cat-local", incomingCategory);
        verify(updateSavingsGoalPersistencePort).update("goal-local", incomingGoal);
        verifyNoMoreInteractions(addCategoryPersistencePort, addSavingsGoalPersistencePort);
    }

    private AccountDto account(String id) {
        AccountDto dto = new AccountDto();
        dto.setId(id);
        dto.setName("Checking-" + id);
        dto.setType(AccountType.CHECKING);
        dto.setBalance(BigDecimal.ZERO);
        return dto;
    }
}
