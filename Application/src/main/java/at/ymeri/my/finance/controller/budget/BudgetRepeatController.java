package at.ymeri.my.finance.controller.budget;

import at.ymeri.my.finance.application.controller.budget.BudgetRepeatApi;
import at.ymeri.my.finance.application.data.RepeatAllocationsRequest;
import at.ymeri.my.finance.application.data.RepeatAllocationsResponse;
import at.ymeri.my.finance.application.mapper.BudgetMapper;
import at.ymeri.my.finance.domain.api.RepeatAllocationsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BudgetRepeatController implements BudgetRepeatApi {

    private final RepeatAllocationsService repeatAllocationsService;

    public BudgetRepeatController(RepeatAllocationsService repeatAllocationsService) {
        this.repeatAllocationsService = repeatAllocationsService;
    }

    @Override
    public ResponseEntity<RepeatAllocationsResponse> repeatAllocations(RepeatAllocationsRequest request) {
        try {
            var applied = repeatAllocationsService.repeatAllocations(
                    request.getFromYear(), request.getFromMonth(), request.getToYear(), request.getToMonth());
            return ResponseEntity.ok(new RepeatAllocationsResponse()
                    .applied(BudgetMapper.INSTANCE.mapTopUps(applied)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
