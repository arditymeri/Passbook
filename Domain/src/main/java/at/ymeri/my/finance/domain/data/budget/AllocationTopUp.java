package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AllocationTopUp {

    private String categoryId;
    private BigDecimal amountAdded;
    private BigDecimal newMonthlyAmount;
}
