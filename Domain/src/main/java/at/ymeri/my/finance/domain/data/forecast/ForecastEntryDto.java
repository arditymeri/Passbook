package at.ymeri.my.finance.domain.data.forecast;

import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class ForecastEntryDto {
    private OffsetDateTime date;
    private String seriesId;
    private TransactionType transactionType;
    private String description;
    private BigDecimal amount;
    private BigDecimal projectedBalance;
}
