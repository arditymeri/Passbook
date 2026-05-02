package at.ymeri.my.finance.domain.spi.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

public interface AddIncomePersistencePort {

    IncomeDto addIncome(IncomeDto incomeDto);
}
