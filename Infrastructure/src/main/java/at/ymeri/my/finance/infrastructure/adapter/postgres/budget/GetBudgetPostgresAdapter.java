package at.ymeri.my.finance.infrastructure.adapter.postgres.budget;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.BudgetMapper;
import at.ymeri.my.finance.infrastructure.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetBudgetPostgresAdapter implements GetBudgetPersistencePort {

    private final BudgetRepository budgetRepository;

    public GetBudgetPostgresAdapter(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    public List<BudgetDto> findByYearAndMonth(int year, int month) {
        return BudgetMapper.INSTANCE.map(budgetRepository.findByYearAndMonth(year, month));
    }

    @Override
    public Optional<BudgetDto> findByCategoryIdAndYearAndMonth(String categoryId, int year, int month) {
        return budgetRepository.findByCategoryIdAndYearAndMonth(categoryId, year, month)
                .map(BudgetMapper.INSTANCE::map);
    }

    @Override
    public Optional<BudgetDto> findById(UUID id) {
        return budgetRepository.findById(id).map(BudgetMapper.INSTANCE::map);
    }

    @Override
    public boolean existsById(UUID id) {
        return budgetRepository.existsById(id);
    }
}
