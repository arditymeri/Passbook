package at.ymeri.my.finance.domain.spi.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;

import java.util.List;
import java.util.Optional;

public interface GetAccountPersistencePort {

    Optional<AccountDto> getAccountById(String id);

    List<AccountDto> getAll();
}
