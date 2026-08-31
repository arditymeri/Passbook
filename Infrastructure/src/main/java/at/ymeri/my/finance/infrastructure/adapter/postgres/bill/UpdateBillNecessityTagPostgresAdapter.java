package at.ymeri.my.finance.infrastructure.adapter.postgres.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import at.ymeri.my.finance.domain.spi.bill.UpdateBillNecessityTagPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateBillNecessityTagPostgresAdapter implements UpdateBillNecessityTagPersistencePort {

    private final BillRepository billRepository;

    public UpdateBillNecessityTagPostgresAdapter(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Override
    public BillDto updateNecessityTag(String billId, NecessityTag tag) {
        BillEntity entity = billRepository.findById(UUID.fromString(billId)).orElseThrow();
        entity.setNecessityTag(tag != null ? tag.name() : null);
        return BillMapper.INSTANCE.map(billRepository.save(entity));
    }
}
