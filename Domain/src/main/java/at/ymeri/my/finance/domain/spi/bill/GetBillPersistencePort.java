package at.ymeri.my.finance.domain.spi.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetBillPersistencePort {

    Optional<BillDto> getBillById(UUID uuid);

    List<BillDto> getAll();
}
