package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;
import java.util.UUID;

public interface GetIncomeService {

    IncomeDto getIncomeById(UUID id);

    List<IncomeDto> getAll();
}
