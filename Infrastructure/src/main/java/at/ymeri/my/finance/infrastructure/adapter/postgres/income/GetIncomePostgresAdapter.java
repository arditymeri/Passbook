package at.ymeri.my.finance.infrastructure.adapter.postgres.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.IncomeMapper;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetIncomePostgresAdapter implements GetIncomePersistencePort {

    private final IncomeRepository incomeRepository;

    public GetIncomePostgresAdapter(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    @Override
    public Optional<IncomeDto> getIncomeById(UUID id) {
        return incomeRepository.findById(id).map(IncomeMapper.INSTANCE::map);
    }

    @Override
    public Optional<IncomeDto> lockIncomeById(UUID id) {
        return this.incomeRepository.findByIdForUpdate(id).map(IncomeMapper.INSTANCE::map);
    }

    @Override
    public List<IncomeDto> getAll() {
        return IncomeMapper.INSTANCE.map(incomeRepository.findAll());
    }
}
