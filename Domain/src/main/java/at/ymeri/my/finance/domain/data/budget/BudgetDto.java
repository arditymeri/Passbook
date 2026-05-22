package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetDto {

    private String id;
    private String categoryId;
    private int year;
    private int month;
    private BigDecimal limitAmount;
}
