package at.ymeri.my.finance.infrastructure.adapter.postgres.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.AccountMapper;
import at.ymeri.my.finance.infrastructure.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetAccountPostgresAdapter implements GetAccountPersistencePort {

    private final AccountRepository accountRepository;

    public GetAccountPostgresAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Optional<AccountDto> getAccountById(String id) {
        return accountRepository.findById(UUID.fromString(id))
                .map(AccountMapper.INSTANCE::map);
    }

    @Override
    public List<AccountDto> getAll() {
        return AccountMapper.INSTANCE.map(accountRepository.findAll());
    }

    @Override
    public List<AccountDto> getByType(String type) {
        return AccountMapper.INSTANCE.map(accountRepository.findByType(type));
    }

    @Override
    public boolean existsByName(String name) {
        return accountRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        return accountRepository.existsByNameAndIdNot(name, UUID.fromString(id));
    }
}
