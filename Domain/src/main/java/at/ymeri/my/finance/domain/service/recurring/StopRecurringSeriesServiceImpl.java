package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.StopRecurringSeriesService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.UpdateRecurringSeriesStatusPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Ends a confirmed series' auto-posting.
 *
 * <p>Mirrors {@code ConfirmRecurringSeriesServiceImpl}: one legal transition, everything else
 * rejected. Only the status moves — already-posted transactions are untouched, and the series keeps
 * its detection history and stays listed, because stopping says the series was real and has ended
 * rather than that the detector was wrong about it.
 */
@Service
public class StopRecurringSeriesServiceImpl implements StopRecurringSeriesService {

    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;
    private final UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort;

    public StopRecurringSeriesServiceImpl(
            GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort,
            UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort) {
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
        this.updateRecurringSeriesStatusPersistencePort = updateRecurringSeriesStatusPersistencePort;
    }

    @Override
    public RecurringSeriesDto stop(String id) {
        RecurringSeriesDto series = getRecurringSeriesPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Recurring series not found: " + id));
        if (series.getStatus() != RecurringSeriesStatus.CONFIRMED) {
            throw new IllegalStateException("Only a CONFIRMED series can be stopped: " + id);
        }
        return updateRecurringSeriesStatusPersistencePort.updateStatus(id, RecurringSeriesStatus.STOPPED);
    }
}
