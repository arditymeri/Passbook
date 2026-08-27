package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.DeleteAccountService;
import at.ymeri.my.finance.domain.spi.account.DeleteAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class DeleteAccountServiceImpl implements DeleteAccountService {

    private final DeleteAccountPersistencePort deleteAccountPersistencePort;
    private final GetAccountPersistencePort getAccountPersistencePort;

    public DeleteAccountServiceImpl(DeleteAccountPersistencePort deleteAccountPersistencePort,
                                    GetAccountPersistencePort getAccountPersistencePort) {
        this.deleteAccountPersistencePort = deleteAccountPersistencePort;
        this.getAccountPersistencePort = getAccountPersistencePort;
    }

    @Override
    public void deleteAccount(String id) {
        getAccountPersistencePort.getAccountById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
        if (deleteAccountPersistencePort.isReferencedByTransaction(id)) {
            throw new IllegalStateException(
                    "Account is referenced by a bill or income (including removed or corrected ones, "
                            + "which are retained permanently) and cannot be deleted");
        }
        deleteAccountPersistencePort.deleteAccount(id);
    }
}
