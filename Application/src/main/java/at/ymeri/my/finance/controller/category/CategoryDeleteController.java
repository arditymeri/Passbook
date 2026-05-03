package at.ymeri.my.finance.controller.category;

import at.ymeri.my.finance.application.controller.category.CategoryDeleteApi;
import at.ymeri.my.finance.domain.api.DeleteCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class CategoryDeleteController implements CategoryDeleteApi {

    private final DeleteCategoryService deleteCategoryService;

    public CategoryDeleteController(DeleteCategoryService deleteCategoryService) {
        this.deleteCategoryService = deleteCategoryService;
    }

    @Override
    public ResponseEntity<Void> deleteCategory(String id) {
        deleteCategoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
