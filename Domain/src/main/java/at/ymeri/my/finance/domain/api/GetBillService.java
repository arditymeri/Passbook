package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.List;
import java.util.UUID;

public interface GetBillService {

    BillDto getBillById(UUID uuid);


    List<BillDto> getAll();
}
