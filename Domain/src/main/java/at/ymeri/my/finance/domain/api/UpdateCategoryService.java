package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.category.CategoryDto;

public interface UpdateCategoryService {

    CategoryDto updateCategory(String id, CategoryDto categoryDto);
}
