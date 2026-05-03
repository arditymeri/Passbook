package at.ymeri.my.finance.controller.category;

import at.ymeri.my.finance.application.controller.category.CategoryCreateApi;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CreateCategoryRequest;
import at.ymeri.my.finance.application.mapper.CategoryMapper;
import at.ymeri.my.finance.domain.api.AddCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryCreateController implements CategoryCreateApi {

    private final AddCategoryService addCategoryService;

    public CategoryCreateController(AddCategoryService addCategoryService) {
        this.addCategoryService = addCategoryService;
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(CreateCategoryRequest createCategoryRequest) {
        CategoryResponse response = CategoryMapper.INSTANCE.map(
                addCategoryService.addCategory(CategoryMapper.INSTANCE.map(createCategoryRequest))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
