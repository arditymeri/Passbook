package at.ymeri.my.finance.infrastructure.adapter.postgres.bill;


import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import org.springframework.stereotype.Service;

@Service
public class AddBillPostgresAdapter implements AddBillPersistencePort {

    private final BillRepository billRepository;

    public AddBillPostgresAdapter(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Override
    public BillDto addBill(BillDto billDto) {
        BillEntity billEntity = BillMapper.INSTANCE.map(billDto);
        BillEntity stored = this.billRepository.save(billEntity);
        return BillMapper.INSTANCE.map(stored);
    }
}
