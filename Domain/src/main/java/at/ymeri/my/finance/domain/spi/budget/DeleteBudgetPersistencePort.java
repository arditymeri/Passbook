package at.ymeri.my.finance.domain.spi.budget;

import java.util.UUID;

public interface DeleteBudgetPersistencePort {

    void deleteById(UUID id);
}
