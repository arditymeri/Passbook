package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetIncomeServiceImpl implements GetIncomeService {

    private final GetIncomePersistencePort getIncomePersistencePort;

    public GetIncomeServiceImpl(GetIncomePersistencePort getIncomePersistencePort) {
        this.getIncomePersistencePort = getIncomePersistencePort;
    }

    @Override
    public IncomeDto getIncomeById(UUID id) {
        return getIncomePersistencePort.getIncomeById(id)
                .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));
    }

    @Override
    public List<IncomeDto> getAll() {
        return getIncomePersistencePort.getAll();
    }
}
