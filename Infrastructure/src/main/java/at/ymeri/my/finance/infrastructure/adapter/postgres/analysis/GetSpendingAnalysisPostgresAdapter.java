package at.ymeri.my.finance.infrastructure.adapter.postgres.analysis;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.analysis.GetSpendingAnalysisPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.mapper.IncomeMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GetSpendingAnalysisPostgresAdapter implements GetSpendingAnalysisPersistencePort {

    private final BillRepository billRepository;
    private final IncomeRepository incomeRepository;

    public GetSpendingAnalysisPostgresAdapter(BillRepository billRepository, IncomeRepository incomeRepository) {
        this.billRepository = billRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    public List<BillDto> getBillsByPeriod(LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        return BillMapper.INSTANCE.map(billRepository.findByTimeBetween(start, end));
    }

    @Override
    public List<IncomeDto> getIncomesByPeriod(LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        return IncomeMapper.INSTANCE.map(incomeRepository.findByTimeBetween(start, end));
    }
}
