package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "account")
@Data
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @ElementCollection
    @CollectionTable(name = "account_currency", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "currency_code")
    private List<String> currencies;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "institution")
    private String institution;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
