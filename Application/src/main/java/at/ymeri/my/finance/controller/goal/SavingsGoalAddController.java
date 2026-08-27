package at.ymeri.my.finance.controller.goal;

import at.ymeri.my.finance.application.controller.goal.GoalAddApi;
import at.ymeri.my.finance.application.data.CreateSavingsGoalRequest;
import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import at.ymeri.my.finance.application.mapper.SavingsGoalMapper;
import at.ymeri.my.finance.domain.api.AddSavingsGoalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

@RestController
public class SavingsGoalAddController implements GoalAddApi {

    private final AddSavingsGoalService addSavingsGoalService;

    public SavingsGoalAddController(AddSavingsGoalService addSavingsGoalService) {
        this.addSavingsGoalService = addSavingsGoalService;
    }

    @Override
    public ResponseEntity<SavingsGoalResponse> createSavingsGoal(CreateSavingsGoalRequest createSavingsGoalRequest) {
        var result = addSavingsGoalService.addGoal(
                createSavingsGoalRequest.getName(),
                BigDecimal.valueOf(createSavingsGoalRequest.getTargetAmount()),
                createSavingsGoalRequest.getTargetDate(),
                createSavingsGoalRequest.getAccountId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(SavingsGoalMapper.INSTANCE.map(result));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }
}
