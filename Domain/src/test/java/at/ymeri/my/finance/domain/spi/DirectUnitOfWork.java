package at.ymeri.my.finance.domain.spi;

import java.util.function.Supplier;

/**
 * Runs the work inline with no transaction. Domain unit tests assert business rules, not commit
 * semantics — a plain Mockito mock of {@link UnitOfWork} would swallow the lambda and the service
 * under test would appear to do nothing at all.
 */
public class DirectUnitOfWork implements UnitOfWork {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void runInTransaction(Runnable work) {
        work.run();
    }
}
