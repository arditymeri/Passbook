package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.account.AccountDto;

public interface UpdateAccountService {

    AccountDto updateAccount(String id, AccountDto accountDto);
}
