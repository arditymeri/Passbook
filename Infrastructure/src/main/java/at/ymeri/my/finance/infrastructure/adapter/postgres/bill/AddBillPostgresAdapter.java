package at.ymeri.my.finance.infrastructure.adapter.postgres.bill;


import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AddBillPostgresAdapter implements AddBillPersistencePort {

    private final BillRepository billRepository;

    public AddBillPostgresAdapter(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    /**
     * {@code recordedAt} is stamped "now" only when the caller didn't already set one — ordinary
     * creation (original bills, correction replacements, reversals) never does, so every row
     * gets a true write-time timestamp. Sync's import merge sets it explicitly to the source
     * device's original write time instead, which is exactly what the correction tie-breaker
     * (research.md R3) needs to compare.
     */
    @Override
    public BillDto addBill(BillDto billDto) {
        BillEntity billEntity = BillMapper.INSTANCE.map(billDto);
        if (billEntity.getRecordedAt() == null) {
            billEntity.setRecordedAt(OffsetDateTime.now());
        }
        BillEntity stored = this.billRepository.save(billEntity);
        return BillMapper.INSTANCE.map(stored);
    }
}
