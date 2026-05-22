package at.ymeri.my.finance.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budget", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"category_id", "year", "month"})
})
@Data
public class BudgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "month", nullable = false)
    private int month;

    @Column(name = "limit_amount", nullable = false)
    private BigDecimal limitAmount;
}
