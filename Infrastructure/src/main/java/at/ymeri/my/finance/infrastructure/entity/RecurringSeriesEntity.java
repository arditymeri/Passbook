package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_series")
@Data
public class RecurringSeriesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "group_key", nullable = false)
    private String groupKey;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "frequency", nullable = false)
    private String frequency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
