package at.ymeri.my.finance.domain.spi.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;

public interface UpdateBillNecessityTagPersistencePort {

    BillDto updateNecessityTag(String billId, NecessityTag tag);
}
