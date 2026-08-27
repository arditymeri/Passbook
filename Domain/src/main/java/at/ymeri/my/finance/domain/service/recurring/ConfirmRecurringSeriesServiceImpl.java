package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.ConfirmRecurringSeriesService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.UpdateRecurringSeriesStatusPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class ConfirmRecurringSeriesServiceImpl implements ConfirmRecurringSeriesService {

    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;
    private final UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort;

    public ConfirmRecurringSeriesServiceImpl(GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort,
                                              UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort) {
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
        this.updateRecurringSeriesStatusPersistencePort = updateRecurringSeriesStatusPersistencePort;
    }

    @Override
    public RecurringSeriesDto confirm(String id) {
        RecurringSeriesDto series = getRecurringSeriesPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Recurring series not found: " + id));
        if (series.getStatus() != RecurringSeriesStatus.PROPOSED) {
            throw new IllegalStateException("Only a PROPOSED series can be confirmed: " + id);
        }
        return updateRecurringSeriesStatusPersistencePort.updateStatus(id, RecurringSeriesStatus.CONFIRMED);
    }
}
