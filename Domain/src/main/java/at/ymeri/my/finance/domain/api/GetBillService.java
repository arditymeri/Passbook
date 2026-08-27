package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.List;
import java.util.UUID;

public interface GetBillService {

    BillDto getBillById(UUID uuid);


    List<BillDto> getAll();

    /**
     * Prior values of the bill identified by {@code id}, newest first, ending with the original.
     * Empty if it has never been corrected.
     */
    List<BillDto> getHistory(UUID id);
}
