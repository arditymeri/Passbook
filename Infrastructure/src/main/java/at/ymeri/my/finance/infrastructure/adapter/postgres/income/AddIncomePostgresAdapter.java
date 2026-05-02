package at.ymeri.my.finance.infrastructure.adapter.postgres.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import at.ymeri.my.finance.infrastructure.mapper.IncomeMapper;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

@Service
public class AddIncomePostgresAdapter implements AddIncomePersistencePort {

    private final IncomeRepository incomeRepository;

    public AddIncomePostgresAdapter(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    @Override
    public IncomeDto addIncome(IncomeDto incomeDto) {
        IncomeEntity entity = IncomeMapper.INSTANCE.map(incomeDto);
        IncomeEntity stored = incomeRepository.save(entity);
        return IncomeMapper.INSTANCE.map(stored);
    }
}
