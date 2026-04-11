package at.ymeri.my.finance.domain.spi.analysis;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.time.LocalDate;
import java.util.List;

public interface GetSpendingAnalysisPersistencePort {

    List<BillDto> getBillsByPeriod(LocalDate from, LocalDate to);

    List<IncomeDto> getIncomesByPeriod(LocalDate from, LocalDate to);
}
