package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringGetApi;
import at.ymeri.my.finance.application.data.RecurringSeriesListResponse;
import at.ymeri.my.finance.application.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringGetController implements RecurringGetApi {

    private final GetRecurringSeriesService getRecurringSeriesService;

    public RecurringGetController(GetRecurringSeriesService getRecurringSeriesService) {
        this.getRecurringSeriesService = getRecurringSeriesService;
    }

    @Override
    public ResponseEntity<RecurringSeriesListResponse> listRecurringSeries() {
        return ResponseEntity.ok(new RecurringSeriesListResponse()
                .series(RecurringSeriesMapper.INSTANCE.mapList(getRecurringSeriesService.getAll())));
    }
}
