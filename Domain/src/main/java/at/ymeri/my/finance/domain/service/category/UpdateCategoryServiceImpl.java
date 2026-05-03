package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.api.UpdateCategoryService;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.UpdateCategoryPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UpdateCategoryServiceImpl implements UpdateCategoryService {

    private final UpdateCategoryPersistencePort updateCategoryPersistencePort;
    private final GetCategoryPersistencePort getCategoryPersistencePort;

    public UpdateCategoryServiceImpl(UpdateCategoryPersistencePort updateCategoryPersistencePort,
                                     GetCategoryPersistencePort getCategoryPersistencePort) {
        this.updateCategoryPersistencePort = updateCategoryPersistencePort;
        this.getCategoryPersistencePort = getCategoryPersistencePort;
    }

    @Override
    public CategoryDto updateCategory(String id, CategoryDto categoryDto) {
        getCategoryPersistencePort.getCategoryById(id)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
        if (categoryDto.getName() == null || categoryDto.getName().isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (categoryDto.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        if (getCategoryPersistencePort.existsByNameAndIdNot(categoryDto.getName(), id)) {
            throw new IllegalStateException("Category name already exists: " + categoryDto.getName());
        }
        return updateCategoryPersistencePort.updateCategory(id, categoryDto);
    }
}
