package at.ymeri.my.finance.domain.spi.recurring;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;

public interface UpdateRecurringSeriesStatusPersistencePort {

    RecurringSeriesDto updateStatus(String id, RecurringSeriesStatus status);
}
