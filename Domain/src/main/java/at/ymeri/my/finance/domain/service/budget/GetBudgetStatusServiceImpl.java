package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.GetBudgetStatusService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.budget.BudgetStatus;
import at.ymeri.my.finance.domain.data.budget.BudgetStatusDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetBudgetStatusServiceImpl implements GetBudgetStatusService {

    private final GetBudgetPersistencePort getBudgetPersistencePort;
    private final GetSpendingAnalysisPersistencePort getSpendingAnalysisPersistencePort;

    public GetBudgetStatusServiceImpl(GetBudgetPersistencePort getBudgetPersistencePort,
                                      GetSpendingAnalysisPersistencePort getSpendingAnalysisPersistencePort) {
        this.getBudgetPersistencePort = getBudgetPersistencePort;
        this.getSpendingAnalysisPersistencePort = getSpendingAnalysisPersistencePort;
    }

    @Override
    public List<BudgetStatusDto> getBudgetStatus(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<BudgetDto> budgets = getBudgetPersistencePort.findByYearAndMonth(year, month);
        List<BillDto> bills = getSpendingAnalysisPersistencePort.getBillsByPeriod(start, end);

        Map<String, BigDecimal> budgetedMap = budgets.stream()
                .collect(Collectors.toMap(BudgetDto::getCategoryId, BudgetDto::getLimitAmount));

        Map<String, BigDecimal> actualMap = bills.stream()
                .filter(b -> b.getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        BillDto::getCategoryId,
                        Collectors.reducing(BigDecimal.ZERO, BillDto::getAmount, BigDecimal::add)));

        Map<String, BudgetStatusDto> merged = new HashMap<>();

        budgetedMap.forEach((catId, budgeted) -> {
            BudgetStatusDto entry = new BudgetStatusDto();
            entry.setCategoryId(catId);
            entry.setBudgeted(budgeted);
            BigDecimal actual = actualMap.getOrDefault(catId, BigDecimal.ZERO);
            entry.setActual(actual);
            entry.setRemaining(budgeted.subtract(actual));
            entry.setStatus(actual.compareTo(budgeted) > 0 ? BudgetStatus.OVER_BUDGET : BudgetStatus.UNDER_BUDGET);
            merged.put(catId, entry);
        });

        actualMap.forEach((catId, actual) -> {
            if (!merged.containsKey(catId)) {
                BudgetStatusDto entry = new BudgetStatusDto();
                entry.setCategoryId(catId);
                entry.setBudgeted(BigDecimal.ZERO);
                entry.setActual(actual);
                entry.setRemaining(BigDecimal.ZERO.subtract(actual));
                entry.setStatus(BudgetStatus.OVER_BUDGET);
                merged.put(catId, entry);
            }
        });

        return new ArrayList<>(merged.values());
    }
}
