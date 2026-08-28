package at.ymeri.my.finance.controller.forecast;

import at.ymeri.my.finance.application.controller.forecast.ForecastGetApi;
import at.ymeri.my.finance.application.data.CashFlowForecastResponse;
import at.ymeri.my.finance.application.mapper.CashFlowForecastMapper;
import at.ymeri.my.finance.domain.api.GetCashFlowForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class CashFlowForecastController implements ForecastGetApi {

    private static final Set<Integer> SUPPORTED_WINDOW_WEEKS = Set.of(2, 4, 8, 12);

    private final GetCashFlowForecastService getCashFlowForecastService;

    public CashFlowForecastController(GetCashFlowForecastService getCashFlowForecastService) {
        this.getCashFlowForecastService = getCashFlowForecastService;
    }

    @Override
    public ResponseEntity<CashFlowForecastResponse> getCashFlowForecast(Integer weeks) {
        if (!SUPPORTED_WINDOW_WEEKS.contains(weeks)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(CashFlowForecastMapper.INSTANCE.map(getCashFlowForecastService.forecast(weeks)));
    }
}
