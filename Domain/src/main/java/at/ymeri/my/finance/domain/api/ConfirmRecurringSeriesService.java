package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;

public interface ConfirmRecurringSeriesService {

    RecurringSeriesDto confirm(String id);
}
