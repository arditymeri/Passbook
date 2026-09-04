package at.ymeri.my.finance.domain.data.bill;

import at.ymeri.my.finance.domain.data.common.PaymentMethod;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class BillDto {

    private String id;
    private String description;
    private BigDecimal amount;
    private String currency;
    private OffsetDateTime time;
    private String categoryId;
    private String payee;
    private PaymentMethod paymentMethod;
    private String accountId;
    private String notes;
    private List<String> tags;
    private boolean recurring;
    private RecurringFrequency recurringFrequency;
    private String externalId;
    private String correctsTransactionId;
    private boolean reversal;
    private NecessityTag necessityTag;
    private OffsetDateTime necessityTagUpdatedAt;
    private OffsetDateTime recordedAt;
}
