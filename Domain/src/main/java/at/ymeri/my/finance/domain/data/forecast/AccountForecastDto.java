package at.ymeri.my.finance.domain.data.forecast;

import at.ymeri.my.finance.domain.data.account.AccountType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AccountForecastDto {
    private String accountId;
    private String accountName;
    private AccountType accountType;
    private BigDecimal currentBalance;
    private int windowWeeks;
    private boolean atRisk;
    private List<ForecastEntryDto> timeline;
}
