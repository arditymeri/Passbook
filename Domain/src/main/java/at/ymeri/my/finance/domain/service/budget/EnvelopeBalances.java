package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.time.ZoneOffset;

/**
 * Cumulative (not month-scoped) balance derivation for envelope budgeting — the unallocated
 * balance and, per category, the envelope balance — computed at read time from allocation,
 * transfer, bill, and income history. Follows the same "derive, never store a running total"
 * pattern {@code GetAccountServiceImpl} already uses for account balances.
 */
@Service
public class EnvelopeBalances {

    private final GetBudgetPersistencePort getBudgetPersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;
    private final GetAllocationTransferPersistencePort getAllocationTransferPersistencePort;
    private final GetBillPersistencePort getBillPersistencePort;

    public EnvelopeBalances(GetBudgetPersistencePort getBudgetPersistencePort,
                             GetIncomePersistencePort getIncomePersistencePort,
                             GetAllocationTransferPersistencePort getAllocationTransferPersistencePort,
                             GetBillPersistencePort getBillPersistencePort) {
        this.getBudgetPersistencePort = getBudgetPersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
        this.getAllocationTransferPersistencePort = getAllocationTransferPersistencePort;
        this.getBillPersistencePort = getBillPersistencePort;
    }

    /**
     * Cumulative income to date minus the cumulative sum of all allocations to date — not reset
     * at month boundaries, so a positive or negative balance from a prior month is automatically
     * included.
     */
    public BigDecimal unallocatedAsOf(int asOfYear, int asOfMonth) {
        OffsetDateTime cutoff = endOfMonth(asOfYear, asOfMonth);

        BigDecimal totalIncome = getIncomePersistencePort.getAll().stream()
                .filter(income -> !income.getTime().isAfter(cutoff))
                .map(IncomeDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllocated = getBudgetPersistencePort.getAll().stream()
                .filter(allocation -> !isAfter(allocation.getYear(), allocation.getMonth(), asOfYear, asOfMonth))
                .map(BudgetDto::getLimitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalIncome.subtract(totalAllocated);
    }

    /**
     * Cumulative amount ever allocated or transferred into this category, minus whatever has been
     * transferred out of it or spent from it, to date. Carries forward automatically — nothing
     * resets at a month boundary.
     */
    public BigDecimal envelopeBalanceAsOf(String categoryId, int asOfYear, int asOfMonth) {
        OffsetDateTime cutoff = endOfMonth(asOfYear, asOfMonth);

        BigDecimal allocated = getBudgetPersistencePort.getAll().stream()
                .filter(allocation -> categoryId.equals(allocation.getCategoryId()))
                .filter(allocation -> !isAfter(allocation.getYear(), allocation.getMonth(), asOfYear, asOfMonth))
                .map(BudgetDto::getLimitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AllocationTransferDto> transfers = getAllocationTransferPersistencePort.getAll().stream()
                .filter(transfer -> !isAfter(transfer.getYear(), transfer.getMonth(), asOfYear, asOfMonth))
                .toList();

        BigDecimal transfersIn = transfers.stream()
                .filter(transfer -> categoryId.equals(transfer.getToCategoryId()))
                .map(AllocationTransferDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal transfersOut = transfers.stream()
                .filter(transfer -> categoryId.equals(transfer.getFromCategoryId()))
                .map(AllocationTransferDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal spent = getBillPersistencePort.getAll().stream()
                .filter(bill -> categoryId.equals(bill.getCategoryId()))
                .filter(bill -> !bill.getTime().isAfter(cutoff))
                .map(BillDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return allocated.add(transfersIn).subtract(transfersOut).subtract(spent);
    }

    private static OffsetDateTime endOfMonth(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return yearMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
    }

    private static boolean isAfter(int year, int month, int asOfYear, int asOfMonth) {
        return YearMonth.of(year, month).isAfter(YearMonth.of(asOfYear, asOfMonth));
    }
}
