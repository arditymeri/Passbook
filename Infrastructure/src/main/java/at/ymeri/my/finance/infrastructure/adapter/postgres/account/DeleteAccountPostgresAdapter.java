package at.ymeri.my.finance.infrastructure.adapter.postgres.account;

import at.ymeri.my.finance.domain.spi.account.DeleteAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.repository.AccountRepository;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteAccountPostgresAdapter implements DeleteAccountPersistencePort {

    private final AccountRepository accountRepository;
    private final BillRepository billRepository;
    private final IncomeRepository incomeRepository;

    public DeleteAccountPostgresAdapter(AccountRepository accountRepository,
                                        BillRepository billRepository,
                                        IncomeRepository incomeRepository) {
        this.accountRepository = accountRepository;
        this.billRepository = billRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    public void deleteAccount(String id) {
        accountRepository.deleteById(UUID.fromString(id));
    }

    @Override
    public boolean isReferencedByTransaction(String accountId) {
        return billRepository.existsByAccountId(accountId)
                || incomeRepository.existsByAccountId(accountId);
    }
}
