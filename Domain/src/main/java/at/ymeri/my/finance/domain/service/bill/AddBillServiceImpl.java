package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.AddBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class AddBillServiceImpl implements AddBillService {

    private final AddBillPersistencePort billPersistencePort;

    public AddBillServiceImpl(AddBillPersistencePort billPersistencePort) {
        this.billPersistencePort = billPersistencePort;
    }

    @Override
    public BillDto addBill(BillDto billDto) {
        return billPersistencePort.addBill(billDto);
    }

}
