package at.ymeri.my.finance.controller.recurring;

import at.ymeri.my.finance.application.controller.recurring.RecurringAutoPostApi;
import at.ymeri.my.finance.application.data.PostedOccurrence;
import at.ymeri.my.finance.application.data.PostingRunResult;
import at.ymeri.my.finance.application.data.RecurringSeriesState;
import at.ymeri.my.finance.application.data.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.api.PostDueOccurrencesService;
import at.ymeri.my.finance.domain.api.StopRecurringSeriesService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * Stopping a confirmed series, and running posting on demand.
 *
 * <p>The on-demand run is the same work the daily schedule does — deliberately, rather than a
 * separate "catch up now" path. Posting derives its work from the ledger every time, so there is
 * only one behaviour to get right and only one to test.
 */
@RestController
public class RecurringAutoPostController implements RecurringAutoPostApi {

    private final PostDueOccurrencesService postDueOccurrencesService;
    private final StopRecurringSeriesService stopRecurringSeriesService;

    public RecurringAutoPostController(PostDueOccurrencesService postDueOccurrencesService,
                                       StopRecurringSeriesService stopRecurringSeriesService) {
        this.postDueOccurrencesService = postDueOccurrencesService;
        this.stopRecurringSeriesService = stopRecurringSeriesService;
    }

    @Override
    public ResponseEntity<PostingRunResult> postDueOccurrences() {
        // The clock is read here, at the edge, so the Domain service stays a pure function of the
        // date it is given — which is what makes its calendar behaviour testable.
        at.ymeri.my.finance.domain.data.recurring.PostingRunResult domainResult =
                postDueOccurrencesService.postDueOccurrences(LocalDate.now());

        PostingRunResult result = new PostingRunResult();
        result.setPostedCount(domainResult.postedCount());
        result.setAlreadyPostedCount(domainResult.alreadyPostedCount());
        result.setSkippedSeriesCount(domainResult.skippedSeriesCount());
        for (var occurrence : domainResult.posted()) {
            PostedOccurrence item = new PostedOccurrence();
            item.setSeriesId(occurrence.seriesId());
            item.setOccurrenceDate(occurrence.occurrenceDate());
            item.setTransactionId(occurrence.transactionId());
            item.setAmount(occurrence.amount() == null ? null : occurrence.amount().toPlainString());
            result.addPostedItem(item);
        }
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<RecurringSeriesState> stopRecurringSeries(String id) {
        RecurringSeriesDto stopped = stopRecurringSeriesService.stop(id);
        RecurringSeriesState state = new RecurringSeriesState();
        state.setId(stopped.getId());
        state.setStatus(RecurringSeriesStatus.fromValue(stopped.getStatus().name()));
        return ResponseEntity.ok(state);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUnknownSeries(NoSuchElementException e) {
        return e.getMessage();
    }

    /** A series that is not confirmed cannot be stopped — proposed, dismissed or already stopped. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleWrongState(IllegalStateException e) {
        return e.getMessage();
    }
}
