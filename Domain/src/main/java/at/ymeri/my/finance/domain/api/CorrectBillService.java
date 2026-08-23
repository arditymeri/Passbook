package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.UUID;

public interface CorrectBillService {

    /**
     * Corrects the bill identified by {@code id} without modifying it. Posts a reversal of its
     * current value plus a new entry carrying {@code correctedValues}, both referencing {@code id}.
     *
     * @return the newly created corrected bill
     */
    BillDto correctBill(UUID id, BillDto correctedValues);
}
