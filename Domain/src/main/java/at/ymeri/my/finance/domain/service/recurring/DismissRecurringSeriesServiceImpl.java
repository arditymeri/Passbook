package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.DismissRecurringSeriesService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.UpdateRecurringSeriesStatusPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Serves both "reject a proposal" (a PROPOSED series) and "stop tracking" (a CONFIRMED series) —
 * both are the same transition to DISMISSED, per data-model.md's state diagram.
 */
@Service
public class DismissRecurringSeriesServiceImpl implements DismissRecurringSeriesService {

    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;
    private final UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort;

    public DismissRecurringSeriesServiceImpl(GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort,
                                              UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort) {
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
        this.updateRecurringSeriesStatusPersistencePort = updateRecurringSeriesStatusPersistencePort;
    }

    @Override
    public RecurringSeriesDto dismiss(String id) {
        RecurringSeriesDto series = getRecurringSeriesPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Recurring series not found: " + id));
        if (series.getStatus() == RecurringSeriesStatus.DISMISSED) {
            throw new IllegalStateException("Series is already dismissed: " + id);
        }
        return updateRecurringSeriesStatusPersistencePort.updateStatus(id, RecurringSeriesStatus.DISMISSED);
    }
}
