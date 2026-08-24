package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.RemoveIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.UnitOfWork;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RemoveIncomeServiceImpl implements RemoveIncomeService {

    private final AddIncomePersistencePort addIncomePersistencePort;
    private final GetIncomePersistencePort getIncomePersistencePort;
    private final UnitOfWork unitOfWork;

    public RemoveIncomeServiceImpl(AddIncomePersistencePort addIncomePersistencePort,
                                   GetIncomePersistencePort getIncomePersistencePort,
                                   UnitOfWork unitOfWork) {
        this.addIncomePersistencePort = addIncomePersistencePort;
        this.getIncomePersistencePort = getIncomePersistencePort;
        this.unitOfWork = unitOfWork;
    }

    /**
     * The locking read, the "not yet superseded" check and the reversal write form one unit —
     * otherwise two concurrent removals both pass the check and the income is reversed twice.
     */
    @Override
    public void removeIncome(UUID id) {
        unitOfWork.runInTransaction(() -> {
            IncomeDto current = getIncomePersistencePort.lockIncomeById(id)
                    .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));

            IncomeCorrections.assertNotSuperseded(getIncomePersistencePort.getAll(), id, "Income");

            addIncomePersistencePort.addIncome(IncomeCorrections.reversalOf(current));
        });
    }
}
