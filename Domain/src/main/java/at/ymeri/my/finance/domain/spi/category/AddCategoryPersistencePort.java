package at.ymeri.my.finance.domain.spi.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;

public interface AddCategoryPersistencePort {

    CategoryDto addCategory(CategoryDto categoryDto);
}

