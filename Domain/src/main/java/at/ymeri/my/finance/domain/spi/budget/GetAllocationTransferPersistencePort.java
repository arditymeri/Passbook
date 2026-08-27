package at.ymeri.my.finance.domain.spi.budget;

import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;

import java.util.List;

public interface GetAllocationTransferPersistencePort {

    List<AllocationTransferDto> getAll();
}
