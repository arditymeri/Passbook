package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;
import java.util.UUID;

public interface GetIncomeService {

    IncomeDto getIncomeById(UUID id);

    List<IncomeDto> getAll();

    /**
     * Prior values of the income identified by {@code id}, newest first, ending with the original.
     * Empty if it has never been corrected.
     */
    List<IncomeDto> getHistory(UUID id);
}
