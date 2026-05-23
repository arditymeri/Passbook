package at.ymeri.my.finance.controller.analysis;

import at.ymeri.my.finance.application.controller.analysis.AnalysisGetApi;
import at.ymeri.my.finance.application.data.MonthlySummary;
import at.ymeri.my.finance.application.data.MonthlySummaryListResponse;
import at.ymeri.my.finance.application.data.MonthlySummaryResponse;
import at.ymeri.my.finance.application.data.MonthlySummaryResponseCommon;
import at.ymeri.my.finance.application.mapper.AnalysisMapper;
import at.ymeri.my.finance.domain.api.GetSpendingAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class AnalysisGetController implements AnalysisGetApi {

    private final GetSpendingAnalysisService getSpendingAnalysisService;

    public AnalysisGetController(GetSpendingAnalysisService getSpendingAnalysisService) {
        this.getSpendingAnalysisService = getSpendingAnalysisService;
    }

    @Override
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month) {
        if (month == null || month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }
        MonthlySummary summary = AnalysisMapper.INSTANCE.map(
                getSpendingAnalysisService.getMonthlySummary(year, month));
        return ResponseEntity.ok(new MonthlySummaryResponse()
                .common(new MonthlySummaryResponseCommon().success(true).message("OK"))
                .summary(summary));
    }

    @Override
    public ResponseEntity<MonthlySummaryListResponse> getPeriodSummary(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        List<MonthlySummary> summaries = AnalysisMapper.INSTANCE.mapList(
                getSpendingAnalysisService.getSummaryForPeriod(from, to));
        return ResponseEntity.ok(new MonthlySummaryListResponse()
                .common(new MonthlySummaryResponseCommon().success(true).message("OK"))
                .summaries(summaries));
    }
}
