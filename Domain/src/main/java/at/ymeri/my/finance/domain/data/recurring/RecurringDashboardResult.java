package at.ymeri.my.finance.domain.data.recurring;

import lombok.Data;

import java.util.List;

@Data
public class RecurringDashboardResult {

    private List<UpcomingRecurringItemDto> upcoming;
    private List<PriceChangeAlertDto> recentPriceChanges;
}
