package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.analysis.MonthlySummaryDto;

import java.time.LocalDate;
import java.util.List;

public interface GetSpendingAnalysisService {

    MonthlySummaryDto getMonthlySummary(int year, int month);

    List<MonthlySummaryDto> getSummaryForPeriod(LocalDate from, LocalDate to);
}
