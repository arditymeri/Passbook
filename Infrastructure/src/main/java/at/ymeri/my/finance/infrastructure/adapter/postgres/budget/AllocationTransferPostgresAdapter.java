package at.ymeri.my.finance.infrastructure.adapter.postgres.budget;

import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.domain.spi.budget.AddAllocationTransferPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.GetAllocationTransferPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.AllocationTransferEntity;
import at.ymeri.my.finance.infrastructure.mapper.AllocationTransferMapper;
import at.ymeri.my.finance.infrastructure.repository.AllocationTransferRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AllocationTransferPostgresAdapter
        implements GetAllocationTransferPersistencePort, AddAllocationTransferPersistencePort {

    private final AllocationTransferRepository allocationTransferRepository;

    public AllocationTransferPostgresAdapter(AllocationTransferRepository allocationTransferRepository) {
        this.allocationTransferRepository = allocationTransferRepository;
    }

    @Override
    public List<AllocationTransferDto> getAll() {
        return AllocationTransferMapper.INSTANCE.map(allocationTransferRepository.findAll());
    }

    @Override
    public AllocationTransferDto add(AllocationTransferDto transfer) {
        AllocationTransferEntity entity = AllocationTransferMapper.INSTANCE.map(transfer);
        AllocationTransferEntity saved = allocationTransferRepository.save(entity);
        return AllocationTransferMapper.INSTANCE.map(saved);
    }
}
