package at.ymeri.my.finance.domain.spi.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;

public interface UpdateCategoryPersistencePort {

    CategoryDto updateCategory(String id, CategoryDto categoryDto);
}
