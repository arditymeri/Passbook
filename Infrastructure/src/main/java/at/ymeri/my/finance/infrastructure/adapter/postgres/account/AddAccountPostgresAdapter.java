package at.ymeri.my.finance.infrastructure.adapter.postgres.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.AccountMapper;
import at.ymeri.my.finance.infrastructure.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AddAccountPostgresAdapter implements AddAccountPersistencePort {

    private final AccountRepository accountRepository;

    public AddAccountPostgresAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public AccountDto addAccount(AccountDto accountDto) {
        return AccountMapper.INSTANCE.map(
                accountRepository.save(AccountMapper.INSTANCE.map(accountDto))
        );
    }
}
