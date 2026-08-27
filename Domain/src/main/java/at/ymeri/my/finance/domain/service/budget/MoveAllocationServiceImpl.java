package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.MoveAllocationService;
import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.domain.data.budget.MoveAllocationResult;
import at.ymeri.my.finance.domain.spi.budget.AddAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class MoveAllocationServiceImpl implements MoveAllocationService {

    private final GetCategoryPersistencePort getCategoryPersistencePort;
    private final EnvelopeBalances envelopeBalances;
    private final AddAllocationTransferPersistencePort addAllocationTransferPersistencePort;

    public MoveAllocationServiceImpl(GetCategoryPersistencePort getCategoryPersistencePort,
                                      EnvelopeBalances envelopeBalances,
                                      AddAllocationTransferPersistencePort addAllocationTransferPersistencePort) {
        this.getCategoryPersistencePort = getCategoryPersistencePort;
        this.envelopeBalances = envelopeBalances;
        this.addAllocationTransferPersistencePort = addAllocationTransferPersistencePort;
    }

    @Override
    public MoveAllocationResult moveAllocation(String fromCategoryId, String toCategoryId, int year, int month, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (fromCategoryId.equals(toCategoryId)) {
            throw new IllegalArgumentException("Source and destination category must differ");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        getCategoryPersistencePort.getCategoryById(fromCategoryId)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + fromCategoryId));
        getCategoryPersistencePort.getCategoryById(toCategoryId)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + toCategoryId));

        BigDecimal available = envelopeBalances.envelopeBalanceAsOf(fromCategoryId, year, month);
        if (amount.compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the source category's available envelope balance of " + available);
        }

        AllocationTransferDto transfer = new AllocationTransferDto();
        transfer.setFromCategoryId(fromCategoryId);
        transfer.setToCategoryId(toCategoryId);
        transfer.setYear(year);
        transfer.setMonth(month);
        transfer.setAmount(amount);
        transfer.setCreatedAt(OffsetDateTime.now());

        AllocationTransferDto saved = addAllocationTransferPersistencePort.add(transfer);

        MoveAllocationResult result = new MoveAllocationResult();
        result.setTransfer(saved);
        result.setFromEnvelopeBalance(envelopeBalances.envelopeBalanceAsOf(fromCategoryId, year, month));
        result.setToEnvelopeBalance(envelopeBalances.envelopeBalanceAsOf(toCategoryId, year, month));
        return result;
    }
}
