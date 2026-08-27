package at.ymeri.my.finance.controller.goal;

import at.ymeri.my.finance.application.controller.goal.GoalDeleteApi;
import at.ymeri.my.finance.domain.api.DeleteSavingsGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class SavingsGoalDeleteController implements GoalDeleteApi {

    private final DeleteSavingsGoalService deleteSavingsGoalService;

    public SavingsGoalDeleteController(DeleteSavingsGoalService deleteSavingsGoalService) {
        this.deleteSavingsGoalService = deleteSavingsGoalService;
    }

    @Override
    public ResponseEntity<Void> deleteSavingsGoal(UUID id) {
        deleteSavingsGoalService.deleteGoal(id.toString());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }
}
