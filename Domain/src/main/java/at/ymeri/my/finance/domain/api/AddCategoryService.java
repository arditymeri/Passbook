package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.category.CategoryDto;

public interface AddCategoryService {

    CategoryDto addCategory(CategoryDto categoryDto);
}
