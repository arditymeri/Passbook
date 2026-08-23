package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.api.DeleteCategoryService;
import at.ymeri.my.finance.domain.spi.category.DeleteCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class DeleteCategoryServiceImpl implements DeleteCategoryService {

    private final DeleteCategoryPersistencePort deleteCategoryPersistencePort;
    private final GetCategoryPersistencePort getCategoryPersistencePort;

    public DeleteCategoryServiceImpl(DeleteCategoryPersistencePort deleteCategoryPersistencePort,
                                     GetCategoryPersistencePort getCategoryPersistencePort) {
        this.deleteCategoryPersistencePort = deleteCategoryPersistencePort;
        this.getCategoryPersistencePort = getCategoryPersistencePort;
    }

    @Override
    public void deleteCategory(String id) {
        getCategoryPersistencePort.getCategoryById(id)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
        if (deleteCategoryPersistencePort.isReferencedByTransaction(id)) {
            throw new IllegalStateException(
                    "Category is referenced by a bill or income (including removed or corrected ones, "
                            + "which are retained permanently) and cannot be deleted");
        }
        deleteCategoryPersistencePort.deleteCategory(id);
    }
}
