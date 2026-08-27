package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.DeleteSavingsGoalService;
import at.ymeri.my.finance.domain.spi.goal.DeleteSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class DeleteSavingsGoalServiceImpl implements DeleteSavingsGoalService {

    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;
    private final DeleteSavingsGoalPersistencePort deleteSavingsGoalPersistencePort;

    public DeleteSavingsGoalServiceImpl(GetSavingsGoalPersistencePort getSavingsGoalPersistencePort,
                                         DeleteSavingsGoalPersistencePort deleteSavingsGoalPersistencePort) {
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
        this.deleteSavingsGoalPersistencePort = deleteSavingsGoalPersistencePort;
    }

    @Override
    public void deleteGoal(String id) {
        getSavingsGoalPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Savings goal not found: " + id));
        deleteSavingsGoalPersistencePort.delete(id);
    }
}
