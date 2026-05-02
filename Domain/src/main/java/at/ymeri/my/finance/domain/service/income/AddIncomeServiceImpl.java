package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.AddIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.AddIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AddIncomeServiceImpl implements AddIncomeService {

    private final AddIncomePersistencePort addIncomePersistencePort;

    public AddIncomeServiceImpl(AddIncomePersistencePort addIncomePersistencePort) {
        this.addIncomePersistencePort = addIncomePersistencePort;
    }

    @Override
    public IncomeDto addIncome(IncomeDto incomeDto) {
        if (incomeDto.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (incomeDto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (incomeDto.getTime() == null) {
            throw new IllegalArgumentException("Time is required");
        }
        return addIncomePersistencePort.addIncome(incomeDto);
    }
}
