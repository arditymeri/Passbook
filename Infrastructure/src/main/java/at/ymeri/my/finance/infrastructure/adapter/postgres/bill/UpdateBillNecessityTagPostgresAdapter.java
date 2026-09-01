package at.ymeri.my.finance.infrastructure.adapter.postgres.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import at.ymeri.my.finance.domain.spi.bill.UpdateBillNecessityTagPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UpdateBillNecessityTagPostgresAdapter implements UpdateBillNecessityTagPersistencePort {

    private final BillRepository billRepository;

    public UpdateBillNecessityTagPostgresAdapter(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    /**
     * Only ever called from the user-driven tag-setting flow (feature 018), never from sync's
     * import merge — that reuses {@code AddBillPersistencePort.addBill} instead (see
     * {@code ApplyMergePlanService}, which constructs a full replacement DTO and lets the same
     * insert-or-merge-by-id mechanism every other entity uses handle it), preserving the source
     * device's original {@code necessityTagUpdatedAt}. So this always stamps "now" unconditionally.
     */
    @Override
    public BillDto updateNecessityTag(String billId, NecessityTag tag) {
        BillEntity entity = billRepository.findById(UUID.fromString(billId)).orElseThrow();
        entity.setNecessityTag(tag != null ? tag.name() : null);
        entity.setNecessityTagUpdatedAt(OffsetDateTime.now());
        return BillMapper.INSTANCE.map(billRepository.save(entity));
    }
}
