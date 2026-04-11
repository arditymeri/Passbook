package at.ymeri.my.finance.domain.spi.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;

import java.util.List;
import java.util.Optional;

public interface GetCategoryPersistencePort {

    Optional<CategoryDto> getCategoryById(String id);

    List<CategoryDto> getAll();

    List<CategoryDto> getByType(CategoryType type);
}
