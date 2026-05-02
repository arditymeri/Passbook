package at.ymeri.my.finance.domain.spi.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetIncomePersistencePort {

    Optional<IncomeDto> getIncomeById(UUID id);

    List<IncomeDto> getAll();
}
