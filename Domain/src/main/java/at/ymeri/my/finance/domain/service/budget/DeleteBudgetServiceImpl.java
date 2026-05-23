package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.DeleteBudgetService;
import at.ymeri.my.finance.domain.spi.budget.DeleteBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeleteBudgetServiceImpl implements DeleteBudgetService {

    private final GetBudgetPersistencePort getBudgetPersistencePort;
    private final DeleteBudgetPersistencePort deleteBudgetPersistencePort;

    public DeleteBudgetServiceImpl(GetBudgetPersistencePort getBudgetPersistencePort,
                                   DeleteBudgetPersistencePort deleteBudgetPersistencePort) {
        this.getBudgetPersistencePort = getBudgetPersistencePort;
        this.deleteBudgetPersistencePort = deleteBudgetPersistencePort;
    }

    @Override
    public void deleteBudget(UUID id) {
        if (!getBudgetPersistencePort.existsById(id)) {
            throw new NoSuchElementException("Budget not found: " + id);
        }
        deleteBudgetPersistencePort.deleteById(id);
    }
}
