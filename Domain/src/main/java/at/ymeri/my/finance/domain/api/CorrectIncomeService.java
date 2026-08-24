package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.UUID;

public interface CorrectIncomeService {

    /**
     * Corrects the income identified by {@code id} without modifying it. Posts a reversal of its
     * current value plus a new entry carrying {@code correctedValues}, both referencing {@code id}.
     *
     * @return the newly created corrected income
     */
    IncomeDto correctIncome(UUID id, IncomeDto correctedValues);
}
