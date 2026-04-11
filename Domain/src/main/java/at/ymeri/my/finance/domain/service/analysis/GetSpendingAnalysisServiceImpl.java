package at.ymeri.my.finance.domain.service.analysis;

import at.ymeri.my.finance.domain.api.GetSpendingAnalysisService;
import at.ymeri.my.finance.domain.data.analysis.MonthlySummaryDto;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetSpendingAnalysisServiceImpl implements GetSpendingAnalysisService {

    private final GetSpendingAnalysisPersistencePort persistencePort;

    public GetSpendingAnalysisServiceImpl(GetSpendingAnalysisPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    public MonthlySummaryDto getMonthlySummary(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<BillDto> bills = persistencePort.getBillsByPeriod(start, end);
        List<IncomeDto> incomes = persistencePort.getIncomesByPeriod(start, end);

        BigDecimal totalExpenses = bills.stream()
                .map(BillDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncome = incomes.stream()
                .map(IncomeDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> spendingByCategory = bills.stream()
                .filter(b -> b.getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        BillDto::getCategoryId,
                        Collectors.reducing(BigDecimal.ZERO, BillDto::getAmount, BigDecimal::add)
                ));

        MonthlySummaryDto summary = new MonthlySummaryDto();
        summary.setYear(year);
        summary.setMonth(month);
        summary.setTotalExpenses(totalExpenses);
        summary.setTotalIncome(totalIncome);
        summary.setNetBalance(totalIncome.subtract(totalExpenses));
        summary.setSpendingByCategory(spendingByCategory);
        return summary;
    }

    @Override
    public List<MonthlySummaryDto> getSummaryForPeriod(LocalDate from, LocalDate to) {
        List<MonthlySummaryDto> summaries = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        while (!cursor.isAfter(to)) {
            summaries.add(getMonthlySummary(cursor.getYear(), cursor.getMonthValue()));
            cursor = cursor.plusMonths(1);
        }
        return summaries;
    }
}
