package at.ymeri.my.finance.domain.api;

import java.util.UUID;

public interface RemoveBillService {

    /**
     * Removes the bill identified by {@code id} by posting a reversal that cancels out its current
     * value, with no replacement. The original row is never modified or deleted.
     */
    void removeBill(UUID id);
}
