package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;

import java.util.List;

public interface GetCategoryService {

    CategoryDto getCategoryById(String id);

    List<CategoryDto> getAll();

    List<CategoryDto> getByType(CategoryType type);
}
