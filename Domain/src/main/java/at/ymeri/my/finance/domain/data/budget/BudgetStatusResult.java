package at.ymeri.my.finance.domain.data.budget;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BudgetStatusResult {

    private List<BudgetStatusDto> entries;
    private BigDecimal unallocated;
}
