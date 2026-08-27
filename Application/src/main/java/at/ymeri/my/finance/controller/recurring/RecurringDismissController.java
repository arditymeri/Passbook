package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringDismissApi;
import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.application.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.domain.api.DismissRecurringSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class RecurringDismissController implements RecurringDismissApi {

    private final DismissRecurringSeriesService dismissRecurringSeriesService;

    public RecurringDismissController(DismissRecurringSeriesService dismissRecurringSeriesService) {
        this.dismissRecurringSeriesService = dismissRecurringSeriesService;
    }

    @Override
    public ResponseEntity<RecurringSeriesResponse> dismissRecurringSeries(UUID id) {
        try {
            return ResponseEntity.ok(RecurringSeriesMapper.INSTANCE.map(dismissRecurringSeriesService.dismiss(id.toString())));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
