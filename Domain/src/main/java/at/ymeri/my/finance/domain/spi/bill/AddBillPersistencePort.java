package at.ymeri.my.finance.domain.spi.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;

public interface AddBillPersistencePort {

    BillDto addBill(BillDto billDto);
}
