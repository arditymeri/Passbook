package at.ymeri.my.finance.controller.goal;

import at.ymeri.my.finance.application.controller.goal.GoalGetApi;
import at.ymeri.my.finance.application.data.SavingsGoalListResponse;
import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import at.ymeri.my.finance.application.mapper.SavingsGoalMapper;
import at.ymeri.my.finance.domain.api.GetSavingsGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class SavingsGoalGetController implements GoalGetApi {

    private final GetSavingsGoalService getSavingsGoalService;

    public SavingsGoalGetController(GetSavingsGoalService getSavingsGoalService) {
        this.getSavingsGoalService = getSavingsGoalService;
    }

    @Override
    public ResponseEntity<SavingsGoalListResponse> listSavingsGoals() {
        SavingsGoalListResponse response = new SavingsGoalListResponse();
        response.setGoals(SavingsGoalMapper.INSTANCE.mapList(getSavingsGoalService.getAll()));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SavingsGoalResponse> getSavingsGoal(UUID id) {
        return ResponseEntity.ok(SavingsGoalMapper.INSTANCE.map(getSavingsGoalService.getById(id.toString())));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }
}
