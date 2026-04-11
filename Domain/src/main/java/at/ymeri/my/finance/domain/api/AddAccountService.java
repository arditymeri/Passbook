package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.account.AccountDto;

public interface AddAccountService {

    AccountDto addAccount(AccountDto accountDto);
}
