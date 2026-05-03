package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.UpdateAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.UpdateAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UpdateAccountServiceImpl implements UpdateAccountService {

    private final UpdateAccountPersistencePort updateAccountPersistencePort;
    private final GetAccountPersistencePort getAccountPersistencePort;

    public UpdateAccountServiceImpl(UpdateAccountPersistencePort updateAccountPersistencePort,
                                    GetAccountPersistencePort getAccountPersistencePort) {
        this.updateAccountPersistencePort = updateAccountPersistencePort;
        this.getAccountPersistencePort = getAccountPersistencePort;
    }

    @Override
    public AccountDto updateAccount(String id, AccountDto accountDto) {
        getAccountPersistencePort.getAccountById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
        AddAccountServiceImpl.validate(accountDto);
        if (getAccountPersistencePort.existsByNameAndIdNot(accountDto.getName(), id)) {
            throw new IllegalStateException("Account name already exists: " + accountDto.getName());
        }
        return updateAccountPersistencePort.updateAccount(id, accountDto);
    }
}
