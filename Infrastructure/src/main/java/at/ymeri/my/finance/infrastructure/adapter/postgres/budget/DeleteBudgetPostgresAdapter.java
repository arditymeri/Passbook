package at.ymeri.my.finance.infrastructure.adapter.postgres.budget;

import at.ymeri.my.finance.domain.spi.budget.DeleteBudgetPersistencePort;
import at.ymeri.my.finance.infrastructure.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteBudgetPostgresAdapter implements DeleteBudgetPersistencePort {

    private final BudgetRepository budgetRepository;

    public DeleteBudgetPostgresAdapter(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    public void deleteById(UUID id) {
        budgetRepository.deleteById(id);
    }
}
