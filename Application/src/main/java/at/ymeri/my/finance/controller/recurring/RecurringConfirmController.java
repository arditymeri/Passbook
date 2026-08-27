package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringConfirmApi;
import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.application.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.domain.api.ConfirmRecurringSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class RecurringConfirmController implements RecurringConfirmApi {

    private final ConfirmRecurringSeriesService confirmRecurringSeriesService;

    public RecurringConfirmController(ConfirmRecurringSeriesService confirmRecurringSeriesService) {
        this.confirmRecurringSeriesService = confirmRecurringSeriesService;
    }

    @Override
    public ResponseEntity<RecurringSeriesResponse> confirmRecurringSeries(UUID id) {
        try {
            return ResponseEntity.ok(RecurringSeriesMapper.INSTANCE.map(confirmRecurringSeriesService.confirm(id.toString())));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
