package at.ymeri.my.finance.domain.service.analysis;

import at.ymeri.my.finance.domain.data.analysis.MonthlySummaryDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSpendingAnalysisServiceImplTest {

    @Mock
    private GetSpendingAnalysisPersistencePort persistencePort;

    @InjectMocks
    private GetSpendingAnalysisServiceImpl service;

    @Test
    void monthlySummary_aggregatesIncomeAndExpensesCorrectly() {
        BillDto bill1 = bill("cat-A", new BigDecimal("100.00"));
        BillDto bill2 = bill("cat-A", new BigDecimal("50.00"));
        IncomeDto income = income(new BigDecimal("500.00"));

        when(persistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of(bill1, bill2));
        when(persistencePort.getIncomesByPeriod(any(), any())).thenReturn(List.of(income));

        MonthlySummaryDto result = service.getMonthlySummary(2026, 5);

        assertEquals(new BigDecimal("500.00"), result.getTotalIncome());
        assertEquals(new BigDecimal("150.00"), result.getTotalExpenses());
        assertEquals(new BigDecimal("350.00"), result.getNetBalance());
    }

    @Test
    void monthlySummary_groupsBillsByCategory() {
        BillDto billA = bill("cat-A", new BigDecimal("100.00"));
        BillDto billB = bill("cat-B", new BigDecimal("60.00"));
        BillDto billA2 = bill("cat-A", new BigDecimal("40.00"));

        when(persistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of(billA, billB, billA2));
        when(persistencePort.getIncomesByPeriod(any(), any())).thenReturn(List.of());

        MonthlySummaryDto result = service.getMonthlySummary(2026, 5);

        assertEquals(new BigDecimal("140.00"), result.getSpendingByCategory().get("cat-A"));
        assertEquals(new BigDecimal("60.00"), result.getSpendingByCategory().get("cat-B"));
    }

    @Test
    void monthlySummary_excludesUncategorisedBillsFromCategoryMap() {
        BillDto categorised = bill("cat-A", new BigDecimal("80.00"));
        BillDto uncategorised = bill(null, new BigDecimal("20.00"));

        when(persistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of(categorised, uncategorised));
        when(persistencePort.getIncomesByPeriod(any(), any())).thenReturn(List.of());

        MonthlySummaryDto result = service.getMonthlySummary(2026, 5);

        assertEquals(new BigDecimal("100.00"), result.getTotalExpenses());
        assertFalse(result.getSpendingByCategory().containsKey(null));
        assertEquals(1, result.getSpendingByCategory().size());
    }

    @Test
    void monthlySummary_emptyMonthReturnsAllZeroes() {
        when(persistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(persistencePort.getIncomesByPeriod(any(), any())).thenReturn(List.of());

        MonthlySummaryDto result = service.getMonthlySummary(2026, 1);

        assertEquals(BigDecimal.ZERO, result.getTotalIncome());
        assertEquals(BigDecimal.ZERO, result.getTotalExpenses());
        assertEquals(BigDecimal.ZERO, result.getNetBalance());
        assertTrue(result.getSpendingByCategory().isEmpty());
    }

    @Test
    void periodSummary_returnsOneEntryPerMonth() {
        when(persistencePort.getBillsByPeriod(any(), any())).thenReturn(List.of());
        when(persistencePort.getIncomesByPeriod(any(), any())).thenReturn(List.of());

        List<MonthlySummaryDto> result = service.getSummaryForPeriod(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertEquals(3, result.size());
        assertEquals(1, result.get(0).getMonth());
        assertEquals(2, result.get(1).getMonth());
        assertEquals(3, result.get(2).getMonth());
    }

    private BillDto bill(String categoryId, BigDecimal amount) {
        BillDto b = new BillDto();
        b.setCategoryId(categoryId);
        b.setAmount(amount);
        b.setTime(OffsetDateTime.now());
        return b;
    }

    private IncomeDto income(BigDecimal amount) {
        IncomeDto i = new IncomeDto();
        i.setAmount(amount);
        i.setTime(OffsetDateTime.now());
        return i;
    }
}
