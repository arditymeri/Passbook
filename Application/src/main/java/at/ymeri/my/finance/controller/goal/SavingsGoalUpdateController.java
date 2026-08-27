package at.ymeri.my.finance.controller.goal;

import at.ymeri.my.finance.application.controller.goal.GoalUpdateApi;
import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import at.ymeri.my.finance.application.data.UpdateSavingsGoalRequest;
import at.ymeri.my.finance.application.mapper.SavingsGoalMapper;
import at.ymeri.my.finance.domain.api.UpdateSavingsGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class SavingsGoalUpdateController implements GoalUpdateApi {

    private final UpdateSavingsGoalService updateSavingsGoalService;

    public SavingsGoalUpdateController(UpdateSavingsGoalService updateSavingsGoalService) {
        this.updateSavingsGoalService = updateSavingsGoalService;
    }

    @Override
    public ResponseEntity<SavingsGoalResponse> updateSavingsGoal(UUID id, UpdateSavingsGoalRequest updateSavingsGoalRequest) {
        var result = updateSavingsGoalService.updateGoal(
                id.toString(),
                updateSavingsGoalRequest.getName(),
                BigDecimal.valueOf(updateSavingsGoalRequest.getTargetAmount()),
                updateSavingsGoalRequest.getTargetDate());
        return ResponseEntity.ok(SavingsGoalMapper.INSTANCE.map(result));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }
}
