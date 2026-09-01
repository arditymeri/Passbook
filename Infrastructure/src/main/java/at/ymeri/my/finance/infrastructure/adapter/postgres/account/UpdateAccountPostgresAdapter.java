package at.ymeri.my.finance.infrastructure.adapter.postgres.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.UpdateAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.AccountEntity;
import at.ymeri.my.finance.infrastructure.mapper.AccountMapper;
import at.ymeri.my.finance.infrastructure.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UpdateAccountPostgresAdapter implements UpdateAccountPersistencePort {

    private final AccountRepository accountRepository;

    public UpdateAccountPostgresAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * {@code updatedAt} is stamped "now" only when the caller didn't already set one — see
     * {@link AddAccountPostgresAdapter#addAccount} for why sync's import merge preserves the
     * source device's original timestamp instead.
     */
    @Override
    public AccountDto updateAccount(String id, AccountDto accountDto) {
        AccountEntity entity = accountRepository.findById(UUID.fromString(id)).orElseThrow();
        entity.setName(accountDto.getName());
        entity.setType(accountDto.getType() != null ? accountDto.getType().name() : null);
        entity.setCurrencies(accountDto.getCurrencies());
        entity.setDefaultCurrency(accountDto.getDefaultCurrency());
        entity.setBalance(accountDto.getBalance());
        entity.setInstitution(accountDto.getInstitution());
        entity.setUpdatedAt(accountDto.getUpdatedAt() != null ? accountDto.getUpdatedAt() : OffsetDateTime.now());
        return AccountMapper.INSTANCE.map(accountRepository.save(entity));
    }
}
