package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;

import java.util.List;

public interface GetRecurringSeriesService {

    List<RecurringSeriesDto> getAll();
}
