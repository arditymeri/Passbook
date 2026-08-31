package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.recurring.RecurringCostSummaryItemDto;

import java.util.List;

public interface GetRecurringCostSummaryService {

    List<RecurringCostSummaryItemDto> getSummary();
}
