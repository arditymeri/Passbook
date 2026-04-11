package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.AddAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class AddAccountServiceImpl implements AddAccountService {

    private final AddAccountPersistencePort addAccountPersistencePort;

    public AddAccountServiceImpl(AddAccountPersistencePort addAccountPersistencePort) {
        this.addAccountPersistencePort = addAccountPersistencePort;
    }

    @Override
    public AccountDto addAccount(AccountDto accountDto) {
        return addAccountPersistencePort.addAccount(accountDto);
    }
}
