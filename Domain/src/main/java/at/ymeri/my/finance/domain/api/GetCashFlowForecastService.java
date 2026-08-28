package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.forecast.CashFlowForecastResult;

public interface GetCashFlowForecastService {
    CashFlowForecastResult forecast(int windowWeeks);
}
