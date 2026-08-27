package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringGetApi;
import at.ymeri.my.finance.application.data.RecurringDashboardResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesListResponse;
import at.ymeri.my.finance.application.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.api.GetUpcomingRecurringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringGetController implements RecurringGetApi {

    private final GetRecurringSeriesService getRecurringSeriesService;
    private final GetUpcomingRecurringService getUpcomingRecurringService;

    public RecurringGetController(GetRecurringSeriesService getRecurringSeriesService,
                                   GetUpcomingRecurringService getUpcomingRecurringService) {
        this.getRecurringSeriesService = getRecurringSeriesService;
        this.getUpcomingRecurringService = getUpcomingRecurringService;
    }

    @Override
    public ResponseEntity<RecurringSeriesListResponse> listRecurringSeries() {
        return ResponseEntity.ok(new RecurringSeriesListResponse()
                .series(RecurringSeriesMapper.INSTANCE.mapList(getRecurringSeriesService.getAll())));
    }

    @Override
    public ResponseEntity<RecurringDashboardResponse> getRecurringDashboard() {
        return ResponseEntity.ok(RecurringSeriesMapper.INSTANCE.map(getUpcomingRecurringService.getDashboard()));
    }
}
