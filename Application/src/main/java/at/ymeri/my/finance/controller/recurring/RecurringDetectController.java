package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringDetectApi;
import at.ymeri.my.finance.application.data.RecurringSeriesListResponse;
import at.ymeri.my.finance.application.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.domain.api.DetectRecurringSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringDetectController implements RecurringDetectApi {

    private final DetectRecurringSeriesService detectRecurringSeriesService;

    public RecurringDetectController(DetectRecurringSeriesService detectRecurringSeriesService) {
        this.detectRecurringSeriesService = detectRecurringSeriesService;
    }

    @Override
    public ResponseEntity<RecurringSeriesListResponse> detectRecurringSeries() {
        return ResponseEntity.ok(new RecurringSeriesListResponse()
                .series(RecurringSeriesMapper.INSTANCE.mapList(detectRecurringSeriesService.detect())));
    }
}
