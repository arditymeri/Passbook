package at.ymeri.my.finance.domain.service.sync;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetBudgetService;
import at.ymeri.my.finance.domain.api.GetCategoryService;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.data.sync.CorrectionConflict;
import at.ymeri.my.finance.domain.data.sync.MergePlanDto;
import at.ymeri.my.finance.domain.data.sync.MergeUpdate;
import at.ymeri.my.finance.domain.data.sync.SyncSnapshotDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ComputeMergePlanServiceTest {

    @Mock
    private GetAccountService getAccountService;
    @Mock
    private GetCategoryService getCategoryService;
    @Mock
    private GetBudgetService getBudgetService;
    @Mock
    private GetRecurringSeriesService getRecurringSeriesService;
    @Mock
    private GetBillPersistencePort getBillPersistencePort;
    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;
    @Mock
    private GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    private ComputeMergePlanService service;

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-02-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ComputeMergePlanService(getAccountService, getCategoryService, getBudgetService,
                getRecurringSeriesService, getBillPersistencePort, getIncomePersistencePort,
                getSavingsGoalPersistencePort, new SyncEntityMatching());
        lenient().when(getAccountService.getAll()).thenReturn(List.of());
        lenient().when(getCategoryService.getAll()).thenReturn(List.of());
        lenient().when(getBudgetService.getAll()).thenReturn(List.of());
        lenient().when(getRecurringSeriesService.getAll()).thenReturn(List.of());
        lenient().when(getBillPersistencePort.getAll()).thenReturn(List.of());
        lenient().when(getIncomePersistencePort.getAll()).thenReturn(List.of());
        lenient().when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of());
    }

    private SyncSnapshotDto emptySnapshot() {
        SyncSnapshotDto snapshot = new SyncSnapshotDto();
        snapshot.setSchemaVersion(1);
        snapshot.setExportedAt(OffsetDateTime.now());
        snapshot.setAccounts(List.of());
        snapshot.setCategories(List.of());
        snapshot.setBudgets(List.of());
        snapshot.setRecurringSeries(List.of());
        snapshot.setBills(List.of());
        snapshot.setIncomes(List.of());
        snapshot.setSavingsGoals(List.of());
        return snapshot;
    }

    private CategoryDto category(String id, String name, OffsetDateTime updatedAt) {
        CategoryDto dto = new CategoryDto();
        dto.setId(id);
        dto.setName(name);
        dto.setType(CategoryType.EXPENSE);
        dto.setUpdatedAt(updatedAt);
        return dto;
    }

    private BudgetDto budget(String id, String categoryId, int year, int month, BigDecimal limit, OffsetDateTime updatedAt) {
        BudgetDto dto = new BudgetDto();
        dto.setId(id);
        dto.setCategoryId(categoryId);
        dto.setYear(year);
        dto.setMonth(month);
        dto.setLimitAmount(limit);
        dto.setUpdatedAt(updatedAt);
        return dto;
    }

    private RecurringSeriesDto series(String id, String groupKey, String description, OffsetDateTime updatedAt) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId(id);
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey(groupKey);
        dto.setDescription(description);
        dto.setFrequency(RecurringFrequency.MONTHLY);
        dto.setStatus(RecurringSeriesStatus.CONFIRMED);
        dto.setUpdatedAt(updatedAt);
        return dto;
    }

    private BillDto bill(String id, String correctsTransactionId, boolean reversal, OffsetDateTime recordedAt) {
        BillDto dto = new BillDto();
        dto.setId(id);
        dto.setDescription("bill-" + id);
        dto.setAmount(BigDecimal.TEN);
        dto.setCurrency("EUR");
        dto.setTime(T1);
        dto.setCorrectsTransactionId(correctsTransactionId);
        dto.setReversal(reversal);
        dto.setRecordedAt(recordedAt);
        return dto;
    }

    @Test
    void category_noLocalMatch_isInsert() {
        CategoryDto incoming = category("cat-new", "Groceries", T1);
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setCategories(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertEquals(List.of(incoming), plan.getCategories().getToInsert());
        assertTrue(plan.getCategories().getToUpdate().isEmpty());
        assertEquals(0, plan.getCategories().getUnchangedCount());
    }

    @Test
    void category_idMatch_laterIncomingUpdatedAt_isUpdate() {
        CategoryDto local = category("cat-1", "Groceries", T1);
        CategoryDto incoming = category("cat-1", "Groceries & Household", T2);
        lenient().when(getCategoryService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setCategories(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertTrue(plan.getCategories().getToInsert().isEmpty());
        assertEquals(1, plan.getCategories().getToUpdate().size());
        MergeUpdate<CategoryDto> update = plan.getCategories().getToUpdate().get(0);
        assertEquals("cat-1", update.localId());
        assertEquals(incoming, update.incoming());
        assertEquals(0, plan.getCategories().getUnchangedCount());
    }

    @Test
    void category_idMatch_earlierIncomingUpdatedAt_isUnchanged() {
        CategoryDto local = category("cat-1", "Groceries", T2);
        CategoryDto incoming = category("cat-1", "Groceries (stale)", T1);
        lenient().when(getCategoryService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setCategories(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertTrue(plan.getCategories().getToInsert().isEmpty());
        assertTrue(plan.getCategories().getToUpdate().isEmpty());
        assertEquals(1, plan.getCategories().getUnchangedCount());
    }

    @Test
    void category_idMatch_equalIncomingUpdatedAt_isUnchanged() {
        CategoryDto local = category("cat-1", "Groceries", T1);
        CategoryDto incoming = category("cat-1", "Groceries", T1);
        lenient().when(getCategoryService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setCategories(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertEquals(1, plan.getCategories().getUnchangedCount());
    }

    @Test
    void category_naturalKeyMatch_differentIds_resolvedByLastModifiedWins() {
        CategoryDto local = category("cat-local", "Utilities", T1);
        CategoryDto incoming = category("cat-remote", "Utilities", T2);
        lenient().when(getCategoryService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setCategories(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertTrue(plan.getCategories().getToInsert().isEmpty());
        assertEquals(1, plan.getCategories().getToUpdate().size());
        assertEquals("cat-local", plan.getCategories().getToUpdate().get(0).localId());
    }

    @Test
    void budget_naturalKeyMatch_differentIds_resolvedByLastModifiedWins() {
        BudgetDto local = budget("budget-local", "cat-1", 2026, 3, BigDecimal.valueOf(100), T1);
        BudgetDto incoming = budget("budget-remote", "cat-1", 2026, 3, BigDecimal.valueOf(150), T2);
        lenient().when(getBudgetService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setBudgets(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertEquals(1, plan.getBudgets().getToUpdate().size());
        assertEquals("budget-local", plan.getBudgets().getToUpdate().get(0).localId());
    }

    @Test
    void recurringSeries_naturalKeyMatch_differentIds_resolvedByLastModifiedWins() {
        RecurringSeriesDto local = series("series-local", "cat-1", "Netflix", T1);
        RecurringSeriesDto incoming = series("series-remote", "cat-1", "  netflix  ", T2);
        lenient().when(getRecurringSeriesService.getAll()).thenReturn(List.of(local));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setRecurringSeries(List.of(incoming));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertEquals(1, plan.getRecurringSeries().getToUpdate().size());
        assertEquals("series-local", plan.getRecurringSeries().getToUpdate().get(0).localId());
    }

    @Test
    void bills_twoSiblingCorrections_laterRecordedAtWins_otherReportedAsConflict() {
        BillDto original = bill("bill-orig", null, false, T1.minusDays(10));
        BillDto correctionA = bill("bill-corr-a", "bill-orig", false, T1);
        BillDto correctionB = bill("bill-corr-b", "bill-orig", false, T2);
        lenient().when(getBillPersistencePort.getAll()).thenReturn(List.of(original, correctionA));
        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setBills(List.of(correctionB));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertEquals(List.of(correctionB), plan.getBills().getToInsert());
        assertEquals(1, plan.getBillCorrectionConflicts().size());
        CorrectionConflict conflict = plan.getBillCorrectionConflicts().get(0);
        assertEquals("bill-orig", conflict.correctsTransactionId());
        assertEquals("bill-corr-b", conflict.winningId());
        assertEquals("bill-corr-a", conflict.losingId());
    }

    @Test
    void bills_reversalRows_excludedFromConflictGrouping() {
        BillDto original = bill("bill-orig", null, false, T1.minusDays(10));
        BillDto reversal = bill("bill-rev", "bill-orig", true, T1);
        BillDto replacement = bill("bill-replacement", "bill-orig", false, T1);
        lenient().when(getBillPersistencePort.getAll()).thenReturn(List.of(original, reversal, replacement));
        SyncSnapshotDto snapshot = emptySnapshot();

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertTrue(plan.getBillCorrectionConflicts().isEmpty());
    }

    @Test
    void reImportingIdenticalSnapshot_producesAllUnchangedPlan() {
        CategoryDto category = category("cat-1", "Groceries", T1);
        BudgetDto budget = budget("budget-1", "cat-1", 2026, 3, BigDecimal.TEN, T1);
        RecurringSeriesDto series = series("series-1", "cat-1", "Netflix", T1);
        AccountDto account = new AccountDto();
        account.setId("acct-1");
        account.setName("Checking");
        account.setType(AccountType.CHECKING);
        account.setBalance(BigDecimal.ZERO);
        account.setUpdatedAt(T1);
        BillDto billDto = bill("bill-1", null, false, T1);
        IncomeDto incomeDto = new IncomeDto();
        incomeDto.setId("income-1");
        incomeDto.setAmount(BigDecimal.TEN);
        incomeDto.setCurrency("EUR");
        incomeDto.setTime(T1);
        incomeDto.setRecordedAt(T1);
        SavingsGoalDto goal = new SavingsGoalDto();
        goal.setId("goal-1");
        goal.setName("Vacation");
        goal.setTargetAmount(BigDecimal.valueOf(1000));
        goal.setUpdatedAt(T1);

        lenient().when(getAccountService.getAll()).thenReturn(List.of(account));
        lenient().when(getCategoryService.getAll()).thenReturn(List.of(category));
        lenient().when(getBudgetService.getAll()).thenReturn(List.of(budget));
        lenient().when(getRecurringSeriesService.getAll()).thenReturn(List.of(series));
        lenient().when(getBillPersistencePort.getAll()).thenReturn(List.of(billDto));
        lenient().when(getIncomePersistencePort.getAll()).thenReturn(List.of(incomeDto));
        lenient().when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of(goal));

        SyncSnapshotDto snapshot = emptySnapshot();
        snapshot.setAccounts(List.of(account));
        snapshot.setCategories(List.of(category));
        snapshot.setBudgets(List.of(budget));
        snapshot.setRecurringSeries(List.of(series));
        snapshot.setBills(List.of(billDto));
        snapshot.setIncomes(List.of(incomeDto));
        snapshot.setSavingsGoals(List.of(goal));

        MergePlanDto plan = service.computeMergePlan(snapshot);

        assertTrue(plan.getAccounts().getToInsert().isEmpty());
        assertTrue(plan.getAccounts().getToUpdate().isEmpty());
        assertEquals(1, plan.getAccounts().getUnchangedCount());

        assertTrue(plan.getCategories().getToInsert().isEmpty());
        assertTrue(plan.getCategories().getToUpdate().isEmpty());
        assertEquals(1, plan.getCategories().getUnchangedCount());

        assertTrue(plan.getBudgets().getToInsert().isEmpty());
        assertTrue(plan.getBudgets().getToUpdate().isEmpty());
        assertEquals(1, plan.getBudgets().getUnchangedCount());

        assertTrue(plan.getRecurringSeries().getToInsert().isEmpty());
        assertTrue(plan.getRecurringSeries().getToUpdate().isEmpty());
        assertEquals(1, plan.getRecurringSeries().getUnchangedCount());

        assertTrue(plan.getBills().getToInsert().isEmpty());
        assertTrue(plan.getBills().getToUpdate().isEmpty());
        assertEquals(1, plan.getBills().getUnchangedCount());

        assertTrue(plan.getIncomes().getToInsert().isEmpty());
        assertEquals(1, plan.getIncomes().getUnchangedCount());

        assertTrue(plan.getSavingsGoals().getToInsert().isEmpty());
        assertTrue(plan.getSavingsGoals().getToUpdate().isEmpty());
        assertEquals(1, plan.getSavingsGoals().getUnchangedCount());

        assertTrue(plan.getBillCorrectionConflicts().isEmpty());
        assertTrue(plan.getIncomeCorrectionConflicts().isEmpty());
    }
}
