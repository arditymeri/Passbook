package at.ymeri.my.finance.domain.spi.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetIncomePersistencePort {

    Optional<IncomeDto> getIncomeById(UUID id);

    /**
     * Same read as {@link #getIncomeById}, but also holds a write lock on the row for the rest of
     * the surrounding transaction. Callers that decide whether to supersede an income must use
     * this: two concurrent corrections of the same row would otherwise both pass the "not yet
     * superseded" check and both write a reversal. Requires an active transaction.
     */
    Optional<IncomeDto> lockIncomeById(UUID id);

    List<IncomeDto> getAll();
}
