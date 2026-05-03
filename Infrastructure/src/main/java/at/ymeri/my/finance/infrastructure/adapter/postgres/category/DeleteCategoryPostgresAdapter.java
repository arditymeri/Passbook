package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.spi.category.DeleteCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteCategoryPostgresAdapter implements DeleteCategoryPersistencePort {

    private final CategoryRepository categoryRepository;
    private final BillRepository billRepository;
    private final IncomeRepository incomeRepository;

    public DeleteCategoryPostgresAdapter(CategoryRepository categoryRepository,
                                         BillRepository billRepository,
                                         IncomeRepository incomeRepository) {
        this.categoryRepository = categoryRepository;
        this.billRepository = billRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    public void deleteCategory(String id) {
        categoryRepository.deleteById(UUID.fromString(id));
    }

    @Override
    public boolean isReferencedByTransaction(String categoryId) {
        return billRepository.existsByCategoryId(categoryId)
                || incomeRepository.existsByCategoryId(categoryId);
    }
}
