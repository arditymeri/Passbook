package at.ymeri.my.finance.infrastructure.adapter.postgres.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.AccountEntity;
import at.ymeri.my.finance.infrastructure.mapper.AccountMapper;
import at.ymeri.my.finance.infrastructure.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AddAccountPostgresAdapter implements AddAccountPersistencePort {

    private final AccountRepository accountRepository;

    public AddAccountPostgresAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * {@code updatedAt} is stamped here only when the caller didn't already set one — ordinary
     * account creation never does, so it gets "now". Sync's import merge sets it explicitly to
     * the source device's original timestamp before calling this port, so that value is
     * preserved instead (Constitution-adjacent: a device's own local "now" must never overwrite
     * another device's true last-modified time, or last-modified-wins comparisons downstream
     * would compare "when was this imported" instead of "when was this actually changed").
     */
    @Override
    public AccountDto addAccount(AccountDto accountDto) {
        AccountEntity entity = AccountMapper.INSTANCE.map(accountDto);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(OffsetDateTime.now());
        }
        return AccountMapper.INSTANCE.map(accountRepository.save(entity));
    }
}
