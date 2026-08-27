package at.ymeri.my.finance.infrastructure.adapter.postgres.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetBillPostgresAdapter implements GetBillPersistencePort {

    private final BillRepository billRepository;

    public GetBillPostgresAdapter(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Override
    public Optional<BillDto> getBillById(UUID uuid) {
        Optional<BillEntity> bill = this.billRepository.findById(uuid);
        return bill.map(BillMapper.INSTANCE::map);

    }

    @Override
    public Optional<BillDto> lockBillById(UUID uuid) {
        return this.billRepository.findByIdForUpdate(uuid).map(BillMapper.INSTANCE::map);
    }

    @Override
    public List<BillDto> getAll() {
        List<BillEntity> entities = this.billRepository.findAll();
        return BillMapper.INSTANCE.map(entities);
    }
}
