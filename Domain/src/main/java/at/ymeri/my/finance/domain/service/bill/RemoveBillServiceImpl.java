package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.RemoveBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RemoveBillServiceImpl implements RemoveBillService {

    private final AddBillPersistencePort addBillPersistencePort;
    private final GetBillPersistencePort getBillPersistencePort;

    public RemoveBillServiceImpl(AddBillPersistencePort addBillPersistencePort,
                                 GetBillPersistencePort getBillPersistencePort) {
        this.addBillPersistencePort = addBillPersistencePort;
        this.getBillPersistencePort = getBillPersistencePort;
    }

    /**
     * Transactional so the locking read, the "not yet superseded" check and the reversal write
     * form one unit — otherwise two concurrent removals both pass the check and the bill is
     * reversed twice.
     */
    @Override
    @Transactional
    public void removeBill(UUID id) {
        BillDto current = getBillPersistencePort.lockBillById(id)
                .orElseThrow(() -> new NoSuchElementException("Bill not found: " + id));

        BillCorrections.assertNotSuperseded(getBillPersistencePort.getAll(), id, "Bill");

        addBillPersistencePort.addBill(BillCorrections.reversalOf(current));
    }
}
