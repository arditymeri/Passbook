package at.ymeri.my.finance.domain.data.forecast;

import lombok.Data;

import java.util.List;

@Data
public class CashFlowForecastResult {
    private List<AccountForecastDto> accounts;
}
