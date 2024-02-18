package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.springframework.stereotype.Service;


public interface AddBillService {
    BillDto addBill(BillDto billDto);

}
