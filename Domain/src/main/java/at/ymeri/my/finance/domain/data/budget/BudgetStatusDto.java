package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetStatusDto {

    private String categoryId;
    private BigDecimal budgeted;
    private BigDecimal actual;
    private BigDecimal remaining;
    private BudgetStatus status;
}
