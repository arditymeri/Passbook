package at.ymeri.my.finance.infrastructure.adapter.postgres.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BudgetEntity;
import at.ymeri.my.finance.infrastructure.mapper.BudgetMapper;
import at.ymeri.my.finance.infrastructure.repository.BudgetRepository;
import org.springframework.stereotype.Service;

@Service
public class SetBudgetPostgresAdapter implements SetBudgetPersistencePort {

    private final BudgetRepository budgetRepository;

    public SetBudgetPostgresAdapter(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    public BudgetDto upsert(BudgetDto budgetDto) {
        BudgetEntity entity = BudgetMapper.INSTANCE.map(budgetDto);
        BudgetEntity saved = budgetRepository.save(entity);
        return BudgetMapper.INSTANCE.map(saved);
    }
}
