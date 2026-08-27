package at.ymeri.my.finance.domain.spi.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetBillPersistencePort {

    Optional<BillDto> getBillById(UUID uuid);

    /**
     * Same read as {@link #getBillById}, but also holds a write lock on the row for the rest of the
     * surrounding transaction. Callers that decide whether to supersede a bill must use this: two
     * concurrent corrections of the same row would otherwise both pass the "not yet superseded"
     * check and both write a reversal. Requires an active transaction.
     */
    Optional<BillDto> lockBillById(UUID uuid);

    List<BillDto> getAll();
}
