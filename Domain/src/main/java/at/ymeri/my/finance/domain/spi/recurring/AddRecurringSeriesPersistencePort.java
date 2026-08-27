package at.ymeri.my.finance.domain.spi.recurring;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;

public interface AddRecurringSeriesPersistencePort {

    RecurringSeriesDto add(RecurringSeriesDto series);
}
