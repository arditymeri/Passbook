package at.ymeri.my.finance.domain.data.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * {@code groupKey} is the transaction-type-specific dimension a series groups on alongside
 * description: a bill's {@code categoryId}, or an income's {@code source} name (income has no
 * category — {@code IncomeSource} is its closest equivalent). Kept as one generic string field
 * rather than two type-specific ones so BILL and INCOME series share one shape.
 */
@Data
public class RecurringSeriesDto {

    private String id;
    private TransactionType transactionType;
    private String groupKey;
    private String description;
    private RecurringFrequency frequency;
    private RecurringSeriesStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
