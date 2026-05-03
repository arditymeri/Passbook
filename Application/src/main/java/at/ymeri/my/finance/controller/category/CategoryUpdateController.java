package at.ymeri.my.finance.controller.category;

import at.ymeri.my.finance.application.controller.category.CategoryUpdateApi;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.UpdateCategoryRequest;
import at.ymeri.my.finance.application.mapper.CategoryMapper;
import at.ymeri.my.finance.domain.api.UpdateCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class CategoryUpdateController implements CategoryUpdateApi {

    private final UpdateCategoryService updateCategoryService;

    public CategoryUpdateController(UpdateCategoryService updateCategoryService) {
        this.updateCategoryService = updateCategoryService;
    }

    @Override
    public ResponseEntity<CategoryResponse> updateCategory(String id, UpdateCategoryRequest updateCategoryRequest) {
        CategoryResponse response = CategoryMapper.INSTANCE.map(
                updateCategoryService.updateCategory(id, CategoryMapper.INSTANCE.map(updateCategoryRequest))
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
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
