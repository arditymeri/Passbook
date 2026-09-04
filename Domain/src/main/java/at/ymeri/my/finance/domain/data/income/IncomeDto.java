package at.ymeri.my.finance.domain.data.income;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class IncomeDto {
    private String id;
    private String description;
    private BigDecimal amount;
    private String currency;
    private OffsetDateTime time;
    private IncomeSource source;
    private String payer;
    private String accountId;
    private String notes;
    private boolean recurring;
    private RecurringFrequency recurringFrequency;
    private String recurringSeriesId;
    private String externalId;
    private String correctsTransactionId;
    private boolean reversal;
    private OffsetDateTime recordedAt;
}
