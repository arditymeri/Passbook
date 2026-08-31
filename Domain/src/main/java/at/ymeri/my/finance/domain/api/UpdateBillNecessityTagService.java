package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;

public interface UpdateBillNecessityTagService {

    BillDto updateNecessityTag(String billId, NecessityTag tag);
}
