package at.ymeri.my.finance.infrastructure.adapter.postgres.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import at.ymeri.my.finance.infrastructure.mapper.IncomeMapper;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AddIncomePostgresAdapter implements AddIncomePersistencePort {

    private final IncomeRepository incomeRepository;

    public AddIncomePostgresAdapter(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    /**
     * {@code recordedAt} is stamped "now" only when the caller didn't already set one — see
     * {@code AddBillPostgresAdapter} for the full rationale (sync's import merge preserves the
     * source device's original write time instead).
     */
    @Override
    public IncomeDto addIncome(IncomeDto incomeDto) {
        IncomeEntity entity = IncomeMapper.INSTANCE.map(incomeDto);
        if (entity.getRecordedAt() == null) {
            entity.setRecordedAt(OffsetDateTime.now());
        }
        IncomeEntity stored = incomeRepository.save(entity);
        return IncomeMapper.INSTANCE.map(stored);
    }
}
