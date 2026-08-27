package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.SetBudgetService;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SetBudgetServiceImpl implements SetBudgetService {

    private final SetBudgetPersistencePort setBudgetPersistencePort;
    private final GetBudgetPersistencePort getBudgetPersistencePort;
    private final GetCategoryPersistencePort getCategoryPersistencePort;

    public SetBudgetServiceImpl(SetBudgetPersistencePort setBudgetPersistencePort,
                                GetBudgetPersistencePort getBudgetPersistencePort,
                                GetCategoryPersistencePort getCategoryPersistencePort) {
        this.setBudgetPersistencePort = setBudgetPersistencePort;
        this.getBudgetPersistencePort = getBudgetPersistencePort;
        this.getCategoryPersistencePort = getCategoryPersistencePort;
    }

    @Override
    public BudgetDto setBudget(BudgetDto budgetDto) {
        if (budgetDto.getLimitAmount() == null || budgetDto.getLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget limit must be greater than zero");
        }
        if (budgetDto.getMonth() < 1 || budgetDto.getMonth() > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        CategoryDto category = getCategoryPersistencePort.getCategoryById(budgetDto.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + budgetDto.getCategoryId()));
        if (category.getType() == CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    "Cannot allocate to an INCOME-only category: " + budgetDto.getCategoryId());
        }

        Optional<BudgetDto> existing = getBudgetPersistencePort
                .findByCategoryIdAndYearAndMonth(budgetDto.getCategoryId(), budgetDto.getYear(), budgetDto.getMonth());

        if (existing.isPresent()) {
            BudgetDto toUpdate = existing.get();
            toUpdate.setLimitAmount(budgetDto.getLimitAmount());
            return setBudgetPersistencePort.upsert(toUpdate);
        }

        return setBudgetPersistencePort.upsert(budgetDto);
    }
}
