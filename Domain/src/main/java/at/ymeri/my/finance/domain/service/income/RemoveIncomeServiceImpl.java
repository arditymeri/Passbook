package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.RemoveIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

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

    @Override
    public void removeIncome(UUID id) {
        IncomeDto current = getIncomePersistencePort.getIncomeById(id)
                .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));

        IncomeCorrections.assertNotSuperseded(getIncomePersistencePort.getAll(), id, "Income");

        addIncomePersistencePort.addIncome(IncomeCorrections.reversalOf(current));
    }
}
