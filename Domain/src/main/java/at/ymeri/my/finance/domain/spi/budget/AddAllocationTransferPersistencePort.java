package at.ymeri.my.finance.domain.spi.budget;

import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;

public interface AddAllocationTransferPersistencePort {

    AllocationTransferDto add(AllocationTransferDto transfer);
}
