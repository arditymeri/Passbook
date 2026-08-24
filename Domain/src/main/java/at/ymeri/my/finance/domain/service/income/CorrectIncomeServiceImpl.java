package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.CorrectIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CorrectIncomeServiceImpl implements CorrectIncomeService {

    private final AddIncomePersistencePort addIncomePersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;

    public CorrectIncomeServiceImpl(AddIncomePersistencePort addIncomePersistencePort,
                                    GetIncomePersistencePort getIncomePersistencePort) {
        this.addIncomePersistencePort = addIncomePersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
    }

    /**
     * Reversal and replacement are written as one unit: a failure between them would otherwise
     * leave an orphan reversal that permanently zeroes the income out with nothing replacing it.
     */
    @Override
    @Transactional
    public IncomeDto correctIncome(UUID id, IncomeDto correctedValues) {
        IncomeDto current = getIncomePersistencePort.getIncomeById(id)
                .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));

        validate(correctedValues);
        IncomeCorrections.assertNotSuperseded(getIncomePersistencePort.getAll(), id, "Income");

        addIncomePersistencePort.addIncome(IncomeCorrections.reversalOf(current));
        return addIncomePersistencePort.addIncome(replacement(current, correctedValues));
    }

    private static void validate(IncomeDto correctedValues) {
        if (correctedValues.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (correctedValues.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (correctedValues.getTime() == null) {
            throw new IllegalArgumentException("Time is required");
        }
    }

    /**
     * The corrected replacement carries the user's new values, dated per its own (possibly changed)
     * time, and points back at the row it replaces.
     */
    private static IncomeDto replacement(IncomeDto current, IncomeDto correctedValues) {
        IncomeDto replacement = new IncomeDto();
        replacement.setAmount(correctedValues.getAmount());
        replacement.setDescription(correctedValues.getDescription());
        replacement.setTime(correctedValues.getTime());
        replacement.setSource(correctedValues.getSource());
        replacement.setAccountId(correctedValues.getAccountId());
        replacement.setCurrency(current.getCurrency());
        replacement.setCorrectsTransactionId(current.getId());
        replacement.setReversal(false);
        return replacement;
    }
}
