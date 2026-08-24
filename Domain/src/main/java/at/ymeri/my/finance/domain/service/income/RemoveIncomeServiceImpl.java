package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.RemoveIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RemoveIncomeServiceImpl implements RemoveIncomeService {

    private final AddIncomePersistencePort addIncomePersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;

    public RemoveIncomeServiceImpl(AddIncomePersistencePort addIncomePersistencePort,
                                   GetIncomePersistencePort getIncomePersistencePort) {
        this.addIncomePersistencePort = addIncomePersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
    }

    /**
     * Transactional so the locking read, the "not yet superseded" check and the reversal write
     * form one unit — otherwise two concurrent removals both pass the check and the income is
     * reversed twice.
     */
    @Override
    @Transactional
    public void removeIncome(UUID id) {
        IncomeDto current = getIncomePersistencePort.lockIncomeById(id)
                .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));

        IncomeCorrections.assertNotSuperseded(getIncomePersistencePort.getAll(), id, "Income");

        addIncomePersistencePort.addIncome(IncomeCorrections.reversalOf(current));
    }
}
