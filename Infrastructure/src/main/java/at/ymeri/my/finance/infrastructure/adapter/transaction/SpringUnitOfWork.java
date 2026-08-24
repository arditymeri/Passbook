package at.ymeri.my.finance.infrastructure.adapter.transaction;

import at.ymeri.my.finance.domain.spi.UnitOfWork;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Spring's transaction manager behind the {@link UnitOfWork} port. Callers reach these methods
 * through the Spring proxy, so the surrounding transaction is real; any RuntimeException thrown by
 * the work rolls it back per Spring's default rules.
 */
@Service
public class SpringUnitOfWork implements UnitOfWork {

    @Override
    @Transactional
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    @Transactional
    public void runInTransaction(Runnable work) {
        work.run();
    }
}
