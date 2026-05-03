package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.api.AddCategoryService;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class AddCategoryServiceImpl implements AddCategoryService {

    private final AddCategoryPersistencePort addCategoryPersistencePort;
    private final GetCategoryPersistencePort getCategoryPersistencePort;

    public AddCategoryServiceImpl(AddCategoryPersistencePort addCategoryPersistencePort,
                                  GetCategoryPersistencePort getCategoryPersistencePort) {
        this.addCategoryPersistencePort = addCategoryPersistencePort;
        this.getCategoryPersistencePort = getCategoryPersistencePort;
    }

    @Override
    public CategoryDto addCategory(CategoryDto categoryDto) {
        if (categoryDto.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (categoryDto.getName().isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (categoryDto.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        if (getCategoryPersistencePort.existsByName(categoryDto.getName())) {
            throw new IllegalStateException("Category name already exists: " + categoryDto.getName());
        }
        return addCategoryPersistencePort.addCategory(categoryDto);
    }
}
