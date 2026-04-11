package at.ymeri.my.finance.domain.spi.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;

public interface AddAccountPersistencePort {

    AccountDto addAccount(AccountDto accountDto);
}
