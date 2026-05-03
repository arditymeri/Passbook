package at.ymeri.my.finance.domain.spi.account;

public interface DeleteAccountPersistencePort {

    void deleteAccount(String id);

    boolean isReferencedByTransaction(String accountId);
}
