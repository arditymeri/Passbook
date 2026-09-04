package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
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

    /**
     * Stable identity of this transaction at its source, present only for rows that arrived through
     * ingestion (feature 022). Null for anything typed by hand and for every row recorded before
     * V2. Write-once: changing it would make the row permanently invisible to deduplication.
     */
    /**
     * The recurring series that produced this transaction, when the app posted it rather than the
     * operator entering it or a statement bringing it (feature 023). Null for every other origin.
     * This is the provenance Principle V requires for a row nobody typed.
     */
    @Column(name = "recurring_series_id")
    private String recurringSeriesId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "corrects_transaction_id")
    private String correctsTransactionId;

    @Column(name = "reversal", nullable = false)
    @ColumnDefault("false")
    private boolean reversal;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;
}
