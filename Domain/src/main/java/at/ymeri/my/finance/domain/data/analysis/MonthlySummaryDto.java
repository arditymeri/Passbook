package at.ymeri.my.finance.domain.data.analysis;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class MonthlySummaryDto {

    private int year;
    private int month;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netBalance;
    private Map<String, BigDecimal> spendingByCategory;
}
