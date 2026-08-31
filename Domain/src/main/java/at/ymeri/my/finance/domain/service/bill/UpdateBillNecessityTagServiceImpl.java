package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.UpdateBillNecessityTagService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import at.ymeri.my.finance.domain.spi.bill.UpdateBillNecessityTagPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UpdateBillNecessityTagServiceImpl implements UpdateBillNecessityTagService {

    private final UpdateBillNecessityTagPersistencePort updateBillNecessityTagPersistencePort;
    private final GetBillService getBillService;

    public UpdateBillNecessityTagServiceImpl(UpdateBillNecessityTagPersistencePort updateBillNecessityTagPersistencePort,
                                             GetBillService getBillService) {
        this.updateBillNecessityTagPersistencePort = updateBillNecessityTagPersistencePort;
        this.getBillService = getBillService;
    }

    /**
     * A tag may only be set on a bill's current, visible value — {@link GetBillService#getAll()}
     * already excludes reversal rows and rows superseded by a correction, so containment in it
     * covers "doesn't exist", "is a reversal", and "has been corrected/removed" in one check.
     */
    @Override
    public BillDto updateNecessityTag(String billId, NecessityTag tag) {
        boolean visible = getBillService.getAll().stream().anyMatch(b -> b.getId().equals(billId));
        if (!visible) {
            throw new NoSuchElementException("Bill not found: " + billId);
        }
        return updateBillNecessityTagPersistencePort.updateNecessityTag(billId, tag);
    }
}
