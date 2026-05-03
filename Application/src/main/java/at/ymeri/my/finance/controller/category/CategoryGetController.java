package at.ymeri.my.finance.controller.category;

import at.ymeri.my.finance.application.controller.category.CategoryGetApi;
import at.ymeri.my.finance.application.data.CategoryListResponse;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CategoryType;
import at.ymeri.my.finance.application.mapper.CategoryMapper;
import at.ymeri.my.finance.domain.api.GetCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class CategoryGetController implements CategoryGetApi {

    private final GetCategoryService getCategoryService;

    public CategoryGetController(GetCategoryService getCategoryService) {
        this.getCategoryService = getCategoryService;
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategoryById(String id) {
        try {
            return ResponseEntity.ok(CategoryMapper.INSTANCE.map(getCategoryService.getCategoryById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<CategoryListResponse> listCategories(String type) {
        List<CategoryResponse> items = type != null
                ? CategoryMapper.INSTANCE.mapList(getCategoryService.getByType(
                        at.ymeri.my.finance.domain.data.category.CategoryType.valueOf(type)))
                : CategoryMapper.INSTANCE.mapList(getCategoryService.getAll());
        return ResponseEntity.ok(new CategoryListResponse().categories(items));
    }
}
