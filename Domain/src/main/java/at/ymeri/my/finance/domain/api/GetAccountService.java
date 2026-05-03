package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.account.AccountDto;

import java.util.List;

public interface GetAccountService {

    AccountDto getAccountById(String id);

    List<AccountDto> getAll();

    List<AccountDto> getByType(String type);
}
