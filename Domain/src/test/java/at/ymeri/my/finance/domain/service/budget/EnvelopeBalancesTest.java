package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvelopeBalancesTest {

    @Mock
    private GetBudgetPersistencePort getBudgetPersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @Mock
    private GetAllocationTransferPersistencePort getAllocationTransferPersistencePort;

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    @InjectMocks
    private EnvelopeBalances envelopeBalances;

    @Test
    void unallocatedAsOf_noIncomeNoAllocations_isZero() {
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());
        when(getBudgetPersistencePort.getAll()).thenReturn(List.of());

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void unallocatedAsOf_incomeOnly_equalsTotalIncome() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("3000.00"))));
        when(getBudgetPersistencePort.getAll()).thenReturn(List.of());

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        assertEquals(new BigDecimal("3000.00"), result);
    }

    @Test
    void unallocatedAsOf_incomeAndAllocationsSameMonth_subtractsAllocations() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("3000.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation(2026, 5, new BigDecimal("1800.00"))));

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        assertEquals(new BigDecimal("1200.00"), result);
    }

    @Test
    void unallocatedAsOf_carriesOverPriorMonthsCumulatively() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 4, new BigDecimal("3000.00")), income(2026, 5, new BigDecimal("3000.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation(2026, 4, new BigDecimal("2800.00"))));

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        // 3000 (Apr income) + 3000 (May income) - 2800 (Apr allocation) = 3200
        assertEquals(new BigDecimal("3200.00"), result);
    }

    @Test
    void unallocatedAsOf_ignoresIncomeAndAllocationsAfterTheAsOfMonth() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("3000.00")), income(2026, 6, new BigDecimal("500.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation(2026, 5, new BigDecimal("1000.00")), allocation(2026, 6, new BigDecimal("200.00"))));

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        assertEquals(new BigDecimal("2000.00"), result);
    }

    @Test
    void unallocatedAsOf_canGoNegativeWhenOverAllocated() {
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income(2026, 5, new BigDecimal("1000.00"))));
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation(2026, 5, new BigDecimal("1500.00"))));

        BigDecimal result = envelopeBalances.unallocatedAsOf(2026, 5);

        assertEquals(new BigDecimal("-500.00"), result);
    }

    @Test
    void envelopeBalanceAsOf_noAllocationsNoSpend_isZero() {
        when(getBudgetPersistencePort.getAll()).thenReturn(List.of());
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll()).thenReturn(List.of());

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void envelopeBalanceAsOf_allocatedWithNoSpend_equalsAllocation() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("cat-A", 2026, 5, new BigDecimal("400.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll()).thenReturn(List.of());

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(new BigDecimal("400.00"), result);
    }

    @Test
    void envelopeBalanceAsOf_allocatedWithSpend_subtractsBills() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("cat-A", 2026, 5, new BigDecimal("400.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll())
                .thenReturn(List.of(bill("cat-A", new BigDecimal("120.00"))));

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(new BigDecimal("280.00"), result);
    }

    @Test
    void envelopeBalanceAsOf_correctedBill_netsOutToPostCorrectionValue() {
        // Feature 008: a correction is the original bill plus a same-category reversal row
        // (negated amount) plus the new corrected bill — summing all three nets to the new amount.
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("cat-A", 2026, 5, new BigDecimal("400.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll()).thenReturn(List.of(
                bill("cat-A", new BigDecimal("120.00")),
                bill("cat-A", new BigDecimal("-120.00")),
                bill("cat-A", new BigDecimal("90.00"))));

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(new BigDecimal("310.00"), result);
    }

    @Test
    void envelopeBalanceAsOf_allocationCarriedInFromPriorMonth_isCumulative() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("cat-A", 2026, 4, new BigDecimal("400.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll())
                .thenReturn(List.of(bill("cat-A", new BigDecimal("50.00"))));

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(new BigDecimal("350.00"), result);
    }

    @Test
    void envelopeBalanceAsOf_ignoresOtherCategories() {
        when(getBudgetPersistencePort.getAll())
                .thenReturn(List.of(allocation("cat-A", 2026, 5, new BigDecimal("400.00")), allocation("cat-B", 2026, 5, new BigDecimal("999.00"))));
        when(getAllocationTransferPersistencePort.getAll()).thenReturn(List.of());
        when(getBillPersistencePort.getAll())
                .thenReturn(List.of(bill("cat-B", new BigDecimal("999.00"))));

        BigDecimal result = envelopeBalances.envelopeBalanceAsOf("cat-A", 2026, 5);

        assertEquals(new BigDecimal("400.00"), result);
    }

    private BillDto bill(String categoryId, BigDecimal amount) {
        BillDto dto = new BillDto();
        dto.setCategoryId(categoryId);
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.of(2026, 5, 20, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        return dto;
    }

    private IncomeDto income(int year, int month, BigDecimal amount) {
        IncomeDto dto = new IncomeDto();
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.of(year, month, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        return dto;
    }

    private BudgetDto allocation(int year, int month, BigDecimal amount) {
        return allocation("cat-A", year, month, amount);
    }

    private BudgetDto allocation(String categoryId, int year, int month, BigDecimal amount) {
        BudgetDto dto = new BudgetDto();
        dto.setCategoryId(categoryId);
        dto.setYear(year);
        dto.setMonth(month);
        dto.setLimitAmount(amount);
        return dto;
    }
}
