package at.ymeri.my.finance.controller.budget;

import at.ymeri.my.finance.application.controller.budget.BudgetDeleteApi;
import at.ymeri.my.finance.domain.api.DeleteBudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class BudgetDeleteController implements BudgetDeleteApi {

    private final DeleteBudgetService deleteBudgetService;

    public BudgetDeleteController(DeleteBudgetService deleteBudgetService) {
        this.deleteBudgetService = deleteBudgetService;
    }

    @Override
    public ResponseEntity<Void> deleteBudget(UUID id) {
        try {
            deleteBudgetService.deleteBudget(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
