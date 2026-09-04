package at.ymeri.my.finance.infrastructure.adapter.postgres.recurring;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.recurring.GetAutoPostedTransactionsPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import at.ymeri.my.finance.infrastructure.mapper.BillMapper;
import at.ymeri.my.finance.infrastructure.mapper.IncomeMapper;
import at.ymeri.my.finance.infrastructure.repository.BillRepository;
import at.ymeri.my.finance.infrastructure.repository.IncomeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads auto-posted transactions through the {@code recurring_series_id} index added in {@code V3}.
 *
 * <p>Two reads rather than one query with a correlated subquery: the candidates, and the reversals
 * that already reference something. Both are derived queries, so Spring Data validates them against
 * the entity at startup — a query built from a hand-written string can only fail once it runs, and
 * this one runs inside statement import, where a failure would surface as a broken import rather
 * than as anything mentioning reconciliation.
 */
@Service
public class AutoPostedTransactionsPostgresAdapter implements GetAutoPostedTransactionsPersistencePort {

    private final BillRepository billRepository;
    private final IncomeRepository incomeRepository;

    public AutoPostedTransactionsPostgresAdapter(BillRepository billRepository,
                                                 IncomeRepository incomeRepository) {
        this.billRepository = billRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    public List<BillDto> supersedableBills(String accountId) {
        Set<String> alreadySuperseded = billRepository
                .findByAccountIdAndCorrectsTransactionIdNotNull(accountId).stream()
                .map(BillEntity::getCorrectsTransactionId)
                .collect(Collectors.toSet());
        return billRepository
                .findByAccountIdAndRecurringSeriesIdNotNullAndReversalFalse(accountId).stream()
                .filter(entity -> !alreadySuperseded.contains(entity.getId().toString()))
                .map(BillMapper.INSTANCE::map)
                .toList();
    }

    @Override
    public List<IncomeDto> supersedableIncomes(String accountId) {
        Set<String> alreadySuperseded = incomeRepository
                .findByAccountIdAndCorrectsTransactionIdNotNull(accountId).stream()
                .map(IncomeEntity::getCorrectsTransactionId)
                .collect(Collectors.toSet());
        return incomeRepository
                .findByAccountIdAndRecurringSeriesIdNotNullAndReversalFalse(accountId).stream()
                .filter(entity -> !alreadySuperseded.contains(entity.getId().toString()))
                .map(IncomeMapper.INSTANCE::map)
                .toList();
    }
}
