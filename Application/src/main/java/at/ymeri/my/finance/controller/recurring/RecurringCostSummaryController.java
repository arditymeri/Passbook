package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringCostSummaryApi;
import at.ymeri.my.finance.application.data.RecurringCostSummaryResponse;
import at.ymeri.my.finance.application.mapper.RecurringCostSummaryMapper;
import at.ymeri.my.finance.domain.api.GetRecurringCostSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringCostSummaryController implements RecurringCostSummaryApi {

    private final GetRecurringCostSummaryService getRecurringCostSummaryService;

    public RecurringCostSummaryController(GetRecurringCostSummaryService getRecurringCostSummaryService) {
        this.getRecurringCostSummaryService = getRecurringCostSummaryService;
    }

    @Override
    public ResponseEntity<RecurringCostSummaryResponse> getRecurringCostSummary() {
        return ResponseEntity.ok(new RecurringCostSummaryResponse()
                .items(RecurringCostSummaryMapper.INSTANCE.mapList(getRecurringCostSummaryService.getSummary())));
    }
}
