package at.ymeri.my.finance.infrastructure.adapter.postgres.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BudgetEntity;
import at.ymeri.my.finance.infrastructure.mapper.BudgetMapper;
import at.ymeri.my.finance.infrastructure.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class SetBudgetPostgresAdapter implements SetBudgetPersistencePort {

    private final BudgetRepository budgetRepository;

    public SetBudgetPostgresAdapter(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    /**
     * {@code updatedAt} is stamped "now" only when the caller didn't already set one — see
     * {@code AddAccountPostgresAdapter} for why sync's import merge preserves the source
     * device's original timestamp instead.
     */
    @Override
    public BudgetDto upsert(BudgetDto budgetDto) {
        BudgetEntity entity = BudgetMapper.INSTANCE.map(budgetDto);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(OffsetDateTime.now());
        }
        BudgetEntity saved = budgetRepository.save(entity);
        return BudgetMapper.INSTANCE.map(saved);
    }
}
