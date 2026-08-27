package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.budget.BudgetStatus;
import at.ymeri.my.finance.domain.data.budget.BudgetStatusDto;
import at.ymeri.my.finance.domain.data.budget.BudgetStatusResult;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBudgetStatusServiceImplTest {

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private GetSpendingAnalysisPersistencePort getSpendingAnalysisPersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    private GetBudgetStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        EnvelopeBalances envelopeBalances = new EnvelopeBalances(
                getBudgetPersistencePort,
                getIncomePersistencePort,
                mock(GetAllocationTransferPersistencePort.class),
                mock(GetBillPersistencePort.class));
        service = new GetBudgetStatusServiceImpl(
                getBudgetPersistencePort, getSpendingAnalysisPersistencePort, envelopeBalances);
    }

    @Test
    void getBudgetStatus_underBudget_returnsCorrectRemainingAndStatus() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-A", 2026, 5, new BigDecimal("500.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(budget("cat-A", 2026, 5, new BigDecimal("500.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-A", new BigDecimal("420.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.getEntries().size());
        BudgetStatusDto entry = result.getEntries().get(0);
        assertEquals("cat-A", entry.getCategoryId());
        assertEquals(new BigDecimal("500.00"), entry.getBudgeted());
        assertEquals(new BigDecimal("420.00"), entry.getActual());
        assertEquals(new BigDecimal("80.00"), entry.getRemaining());
        assertEquals(BudgetStatus.UNDER_BUDGET, entry.getStatus());
    }

    @Test
    void getBudgetStatus_overBudget_returnsNegativeRemainingAndOverStatus() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-B", 2026, 5, new BigDecimal("100.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(budget("cat-B", 2026, 5, new BigDecimal("100.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-B", new BigDecimal("150.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.getEntries().size());
        assertEquals(new BigDecimal("-50.00"), result.getEntries().get(0).getRemaining());
        assertEquals(BudgetStatus.OVER_BUDGET, result.getEntries().get(0).getStatus());
    }

    @Test
    void getBudgetStatus_spendWithNoBudget_appearsAsOverBudgetWithZeroBudgeted() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of());
        when(getBudgetPersistencePort.getAll()).thenReturn(List.of());
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-C", new BigDecimal("60.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.getEntries().size());
        assertEquals(BigDecimal.ZERO, result.getEntries().get(0).getBudgeted());
        assertEquals(new BigDecimal("60.00"), result.getEntries().get(0).getActual());
        assertEquals(BudgetStatus.OVER_BUDGET, result.getEntries().get(0).getStatus());
    }

    @Test
    void getBudgetStatus_budgetWithNoSpend_showsZeroActualAndUnderBudget() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-D", 2026, 5, new BigDecimal("200.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(budget("cat-D", 2026, 5, new BigDecimal("200.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.getEntries().size());
        assertEquals(BigDecimal.ZERO, result.getEntries().get(0).getActual());
        assertEquals(new BigDecimal("200.00"), result.getEntries().get(0).getRemaining());
        assertEquals(BudgetStatus.UNDER_BUDGET, result.getEntries().get(0).getStatus());
    }

    @Test
    void getBudgetStatus_noAllocations_unallocatedEqualsTotalIncome() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of());
        when(getBudgetPersistencePort.getAll()).thenReturn(List.of());
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("3000.00"))));

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(new BigDecimal("3000.00"), result.getUnallocated());
    }

    @Test
    void getBudgetStatus_allocationsInCurrentMonth_reduceUnallocated() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-A", 2026, 5, new BigDecimal("500.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(budget("cat-A", 2026, 5, new BigDecimal("500.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("3000.00"))));

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(new BigDecimal("2500.00"), result.getUnallocated());
    }

    @Test
    void getBudgetStatus_allocationsCarriedInFromPriorMonth_stillReduceUnallocated() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of());
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(budget("cat-A", 2026, 4, new BigDecimal("800.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(
                income(2026, 4, new BigDecimal("3000.00")),
                income(2026, 5, new BigDecimal("3000.00"))));

        BudgetStatusResult result = service.getBudgetStatus(2026, 5);

        assertEquals(new BigDecimal("5200.00"), result.getUnallocated());
    }

    private BudgetDto budget(String categoryId, int year, int month, BigDecimal limit) {
        BudgetDto dto = new BudgetDto();
        dto.setCategoryId(categoryId);
        dto.setYear(year);
        dto.setMonth(month);
        dto.setLimitAmount(limit);
        return dto;
    }

    private BillDto bill(String categoryId, BigDecimal amount) {
        BillDto dto = new BillDto();
        dto.setCategoryId(categoryId);
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.now());
        return dto;
    }

    private IncomeDto income(int year, int month, BigDecimal amount) {
        IncomeDto dto = new IncomeDto();
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.of(year, month, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        return dto;
    }
}
