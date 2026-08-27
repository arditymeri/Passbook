package at.ymeri.my.finance.domain.api;

import java.util.UUID;

public interface RemoveIncomeService {

    /**
     * Removes the income identified by {@code id} by posting a reversal that cancels out its
     * current value, with no replacement. The original row is never modified or deleted.
     */
    void removeIncome(UUID id);
}
