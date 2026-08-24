package at.ymeri.my.finance.domain.spi;

import java.util.function.Supplier;

/**
 * Runs a block of domain work as one atomic unit.
 *
 * <p>Domain services that write more than one row — a correction writes a reversal <em>and</em> a
 * replacement — need those writes to commit together or not at all. Expressing that as a port keeps
 * the transaction boundary where the invariant lives (the service that owns it) while leaving the
 * Domain module free of any framework annotation (Constitution Principle VIII); the adapter in
 * Infrastructure supplies the actual transaction semantics.
 *
 * <p>Implementations MUST roll the unit back if the work throws.
 */
public interface UnitOfWork {

    /**
     * Runs {@code work} atomically and returns its result.
     */
    <T> T inTransaction(Supplier<T> work);

    /**
     * Runs {@code work} atomically. Named apart from {@link #inTransaction} on purpose: a lambda
     * whose body is a single method call is both value- and void-compatible, so a same-named
     * overload pair would be ambiguous at every call site.
     */
    void runInTransaction(Runnable work);
}
