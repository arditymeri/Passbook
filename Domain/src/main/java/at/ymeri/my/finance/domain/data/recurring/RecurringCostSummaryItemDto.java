package at.ymeri.my.finance.domain.data.recurring;

import lombok.Data;

import java.math.BigDecimal;

/**
 * One confirmed recurring series' cost, normalized to a monthly-equivalent so series of different
 * frequencies (DAILY/WEEKLY/MONTHLY/YEARLY) can be ranked and summed together, plus whether its
 * amount has crept up since its earliest recorded occurrence.
 */
@Data
public class RecurringCostSummaryItemDto {

    private String seriesId;
    private String description;
    private BigDecimal monthlyEquivalentAmount;
    private BigDecimal originalAmount;
    private boolean priceIncreased;
    private BigDecimal increaseAmount;
}
