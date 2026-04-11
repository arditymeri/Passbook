package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.api.AddCategoryService;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class AddCategoryServiceImpl implements AddCategoryService {

    private final AddCategoryPersistencePort addCategoryPersistencePort;

    public AddCategoryServiceImpl(AddCategoryPersistencePort addCategoryPersistencePort) {
        this.addCategoryPersistencePort = addCategoryPersistencePort;
    }

    @Override
    public CategoryDto addCategory(CategoryDto categoryDto) {
        return addCategoryPersistencePort.addCategory(categoryDto);
    }
}
