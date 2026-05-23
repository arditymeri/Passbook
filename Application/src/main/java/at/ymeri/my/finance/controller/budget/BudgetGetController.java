package at.ymeri.my.finance.controller.budget;

import at.ymeri.my.finance.application.controller.budget.BudgetGetApi;
import at.ymeri.my.finance.application.data.BudgetListResponse;
import at.ymeri.my.finance.application.data.BudgetStatusEntry;
import at.ymeri.my.finance.application.data.BudgetStatusResponse;
import at.ymeri.my.finance.application.mapper.BudgetMapper;
import at.ymeri.my.finance.domain.api.GetBudgetService;
import at.ymeri.my.finance.domain.api.GetBudgetStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BudgetGetController implements BudgetGetApi {

    private final GetBudgetService getBudgetService;
    private final GetBudgetStatusService getBudgetStatusService;

    public BudgetGetController(GetBudgetService getBudgetService,
                               GetBudgetStatusService getBudgetStatusService) {
        this.getBudgetService = getBudgetService;
        this.getBudgetStatusService = getBudgetStatusService;
    }

    @Override
    public ResponseEntity<BudgetListResponse> listBudgets(Integer year, Integer month) {
        if (month == null || month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new BudgetListResponse()
                .budgets(BudgetMapper.INSTANCE.mapList(getBudgetService.getByYearAndMonth(year, month))));
    }

    @Override
    public ResponseEntity<BudgetStatusResponse> getBudgetStatus(Integer year, Integer month) {
        if (month == null || month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }
        List<BudgetStatusEntry> entries = BudgetMapper.INSTANCE
                .mapStatusList(getBudgetStatusService.getBudgetStatus(year, month));
        return ResponseEntity.ok(new BudgetStatusResponse()
                .year(year)
                .month(month)
                .entries(entries));
    }
}
