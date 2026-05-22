package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.budget.BudgetStatus;
import at.ymeri.my.finance.domain.data.budget.BudgetStatusDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBudgetStatusServiceImplTest {

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private GetSpendingAnalysisPersistencePort getSpendingAnalysisPersistencePort;

    @InjectMocks
    private GetBudgetStatusServiceImpl service;

    @Test
    void getBudgetStatus_underBudget_returnsCorrectRemainingAndStatus() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-A", new BigDecimal("500.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-A", new BigDecimal("420.00"))));

        List<BudgetStatusDto> result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.size());
        BudgetStatusDto entry = result.get(0);
        assertEquals("cat-A", entry.getCategoryId());
        assertEquals(new BigDecimal("500.00"), entry.getBudgeted());
        assertEquals(new BigDecimal("420.00"), entry.getActual());
        assertEquals(new BigDecimal("80.00"), entry.getRemaining());
        assertEquals(BudgetStatus.UNDER_BUDGET, entry.getStatus());
    }

    @Test
    void getBudgetStatus_overBudget_returnsNegativeRemainingAndOverStatus() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-B", new BigDecimal("100.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-B", new BigDecimal("150.00"))));

        List<BudgetStatusDto> result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("-50.00"), result.get(0).getRemaining());
        assertEquals(BudgetStatus.OVER_BUDGET, result.get(0).getStatus());
    }

    @Test
    void getBudgetStatus_spendWithNoBudget_appearsAsOverBudgetWithZeroBudgeted() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5)).thenReturn(List.of());
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any()))
                .thenReturn(List.of(bill("cat-C", new BigDecimal("60.00"))));

        List<BudgetStatusDto> result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.ZERO, result.get(0).getBudgeted());
        assertEquals(new BigDecimal("60.00"), result.get(0).getActual());
        assertEquals(BudgetStatus.OVER_BUDGET, result.get(0).getStatus());
    }

    @Test
    void getBudgetStatus_budgetWithNoSpend_showsZeroActualAndUnderBudget() {
        when(getBudgetPersistencePort.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(budget("cat-D", new BigDecimal("200.00"))));
        when(getSpendingAnalysisPersistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());

        List<BudgetStatusDto> result = service.getBudgetStatus(2026, 5);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.ZERO, result.get(0).getActual());
        assertEquals(new BigDecimal("200.00"), result.get(0).getRemaining());
        assertEquals(BudgetStatus.UNDER_BUDGET, result.get(0).getStatus());
    }

    private BudgetDto budget(String categoryId, BigDecimal limit) {
        BudgetDto dto = new BudgetDto();
        dto.setCategoryId(categoryId);
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
}
