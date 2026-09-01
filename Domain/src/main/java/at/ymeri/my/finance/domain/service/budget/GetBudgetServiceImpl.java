package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.GetBudgetService;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetBudgetServiceImpl implements GetBudgetService {

    private final GetBudgetPersistencePort getBudgetPersistencePort;

    public GetBudgetServiceImpl(GetBudgetPersistencePort getBudgetPersistencePort) {
        this.getBudgetPersistencePort = getBudgetPersistencePort;
    }

    @Override
    public List<BudgetDto> getByYearAndMonth(int year, int month) {
        return getBudgetPersistencePort.findByYearAndMonth(year, month);
    }

    @Override
    public Optional<BudgetDto> getById(UUID id) {
        return getBudgetPersistencePort.findById(id);
    }

    @Override
    public List<BudgetDto> getAll() {
        return getBudgetPersistencePort.getAll();
    }
}
