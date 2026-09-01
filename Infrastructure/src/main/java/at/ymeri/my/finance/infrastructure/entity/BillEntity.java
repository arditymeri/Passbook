package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bill")
@Data
public class BillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "description")
    private String description;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "time")
    private OffsetDateTime time;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "corrects_transaction_id")
    private String correctsTransactionId;

    @Column(name = "reversal", nullable = false)
    @ColumnDefault("false")
    private boolean reversal;

    @Column(name = "necessity_tag")
    private String necessityTag;

    @Column(name = "necessity_tag_updated_at")
    private OffsetDateTime necessityTagUpdatedAt;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;
}
