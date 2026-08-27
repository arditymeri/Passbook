package at.ymeri.my.finance.domain.data.recurring;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class UpcomingRecurringItemDto {

    private String seriesId;
    private TransactionType transactionType;
    private String groupKey;
    private String description;
    private OffsetDateTime predictedDate;
    private BigDecimal predictedAmount;
    private boolean overdue;
}
