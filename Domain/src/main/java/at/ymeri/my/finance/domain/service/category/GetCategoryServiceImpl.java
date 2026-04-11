package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.api.GetCategoryService;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetCategoryServiceImpl implements GetCategoryService {

    private final GetCategoryPersistencePort getCategoryPersistencePort;

    public GetCategoryServiceImpl(GetCategoryPersistencePort getCategoryPersistencePort) {
        this.getCategoryPersistencePort = getCategoryPersistencePort;
    }

    @Override
    public CategoryDto getCategoryById(String id) {
        return getCategoryPersistencePort.getCategoryById(id).orElseThrow();
    }

    @Override
    public List<CategoryDto> getAll() {
        return getCategoryPersistencePort.getAll();
    }

    @Override
    public List<CategoryDto> getByType(CategoryType type) {
        return getCategoryPersistencePort.getByType(type);
    }
}
