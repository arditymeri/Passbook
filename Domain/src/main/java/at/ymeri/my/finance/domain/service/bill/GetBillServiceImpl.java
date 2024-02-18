package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetBillServiceImpl implements GetBillService {

    private final GetBillPersistencePort getBillPersistencePort;

    public GetBillServiceImpl(GetBillPersistencePort getBillPersistencePort) {
        this.getBillPersistencePort = getBillPersistencePort;
    }

    @Override
    public BillDto getBillById(UUID uuid) {
        return this.getBillPersistencePort.getBillById(uuid)
                .orElseThrow();
    }

    @Override
    public List<BillDto> getAll() {
        return this.getBillPersistencePort.getAll();
    }
}
