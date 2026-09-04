package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.api.IngestTransactionsService;
import at.ymeri.my.finance.domain.api.ReconcileAutoPostedService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.ingestion.IngestionResult;
import at.ymeri.my.finance.domain.data.ingestion.RowOutcome;
import at.ymeri.my.finance.domain.data.ingestion.StatementRow;
import at.ymeri.my.finance.domain.data.ingestion.TransactionDirection;
import at.ymeri.my.finance.domain.spi.ingestion.IngestTransactionsPersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records statement rows, exactly once each.
 *
 * <p><strong>Create-only.</strong> Nothing here updates or deletes an existing transaction
 * (Principle I). A statement that restates a line the operator already has arrives as a new
 * transaction; correcting the old one stays their decision, through the existing compensating-entry
 * path.
 *
 * <p><strong>Outcomes come from the write.</strong> A row is reported as recorded because the
 * database said it inserted it, and as already-recorded because it did not — never because a lookup
 * beforehand said so. That distinction is the whole of FR-005: a lookup can be stale by the time the
 * write happens, and two concurrent imports would both find nothing and both write.
 */
@Service
public class IngestTransactionsServiceImpl implements IngestTransactionsService {

    private final IngestTransactionsPersistencePort persistencePort;
    private final ReconcileAutoPostedService reconcileAutoPostedService;

    public IngestTransactionsServiceImpl(IngestTransactionsPersistencePort persistencePort,
                                         ReconcileAutoPostedService reconcileAutoPostedService) {
        this.persistencePort = persistencePort;
        this.reconcileAutoPostedService = reconcileAutoPostedService;
    }

    @Override
    public IngestionResult ingest(List<StatementRow> rows, String accountId,
                                  Set<Integer> excludedRowIndexes) {
        Set<Integer> excluded = excludedRowIndexes == null ? Set.of() : new HashSet<>(excludedRowIndexes);

        List<StatementRow> writable = new ArrayList<>();
        List<RowOutcome> outcomes = new ArrayList<>(rows.size());

        for (StatementRow row : rows) {
            if (row.isRejected()) {
                outcomes.add(RowOutcome.rejected(row.rowIndex(), row.rejectionReason()));
            } else if (excluded.contains(row.rowIndex())) {
                // Nothing is recorded about an exclusion — not even a tombstone — so the same row is
                // offered again as new on a later import of the same statement (FR-014).
                outcomes.add(RowOutcome.excluded(row.rowIndex()));
            } else {
                writable.add(row);
            }
        }

        List<BillDto> bills = writable.stream()
                .filter(row -> row.direction() == TransactionDirection.BILL)
                .map(row -> toBill(row, accountId))
                .toList();
        List<IncomeDto> incomes = writable.stream()
                .filter(row -> row.direction() == TransactionDirection.INCOME)
                .map(row -> toIncome(row, accountId))
                .toList();

        Map<String, String> insertedByIdentity = persistencePort.insertNew(bills, incomes);

        for (StatementRow row : writable) {
            String transactionId = insertedByIdentity.get(row.externalId());
            outcomes.add(transactionId != null
                    ? RowOutcome.recorded(row.rowIndex(), transactionId)
                    : RowOutcome.alreadyRecorded(row.rowIndex()));
        }

        // Feature 023: the bank's own version of a transaction this app predicted supersedes the
        // prediction, so the operator is not left holding both. Done here rather than in the
        // controller so any future producer that ingests — the Kafka consumer 022 kept the door open
        // for — gets it without rewiring. Rows that supersede something are still reported RECORDED:
        // the supersession is an additional effect, not a different outcome.
        List<String> recordedBillIds = new ArrayList<>();
        List<String> recordedIncomeIds = new ArrayList<>();
        for (StatementRow row : writable) {
            String transactionId = insertedByIdentity.get(row.externalId());
            if (transactionId == null) {
                continue;
            }
            if (row.direction() == TransactionDirection.BILL) {
                recordedBillIds.add(transactionId);
            } else {
                recordedIncomeIds.add(transactionId);
            }
        }
        reconcileAutoPostedService.reconcileBills(recordedBillIds);
        reconcileAutoPostedService.reconcileIncomes(recordedIncomeIds);

        outcomes.sort(java.util.Comparator.comparingInt(RowOutcome::rowIndex));
        return new IngestionResult(List.copyOf(outcomes));
    }

    private BillDto toBill(StatementRow row, String accountId) {
        BillDto bill = new BillDto();
        bill.setDescription(row.description());
        bill.setAmount(row.amount());
        bill.setTime(atStartOfDayUtc(row));
        bill.setAccountId(accountId);
        bill.setExternalId(row.externalId());
        return bill;
    }

    private IncomeDto toIncome(StatementRow row, String accountId) {
        IncomeDto income = new IncomeDto();
        income.setDescription(row.description());
        income.setAmount(row.amount());
        income.setTime(atStartOfDayUtc(row));
        income.setAccountId(accountId);
        income.setExternalId(row.externalId());
        return income;
    }

    /**
     * Statements state dates, not times. Midnight UTC is a deliberate, uniform choice — and note
     * that identity is derived from the calendar date rather than from this value, so the invented
     * time-of-day can never influence whether two rows are considered the same transaction.
     */
    private static OffsetDateTime atStartOfDayUtc(StatementRow row) {
        return row.date().atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
