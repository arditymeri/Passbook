package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "income")
@Data
public class IncomeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Column(name = "source")
    private String source;

    @Column(name = "payer")
    private String payer;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recurring")
    private boolean recurring;

    @Column(name = "recurring_frequency")
    private String recurringFrequency;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "corrects_transaction_id")
    private String correctsTransactionId;

    @Column(name = "reversal")
    private boolean reversal;
}
