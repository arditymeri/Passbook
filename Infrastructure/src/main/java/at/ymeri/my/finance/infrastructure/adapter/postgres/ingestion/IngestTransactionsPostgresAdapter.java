package at.ymeri.my.finance.infrastructure.adapter.postgres.ingestion;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.ingestion.IngestTransactionsPersistencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes ingested transactions with the uniqueness decision left to the database.
 *
 * <p><strong>Why raw SQL here.</strong> The guarantee this feature exists to provide cannot be
 * expressed through the JPA repositories: it needs {@code INSERT … ON CONFLICT DO NOTHING RETURNING},
 * which reports back exactly the rows that landed, in one statement, under one lock. JPA would force
 * a read-then-write per row, which is the race described in the port's contract. The constitution's
 * Development Workflow permits raw SQL exclusively in Infrastructure adapters, and this is one.
 *
 * <p><strong>The conflict target must repeat the index predicate.</strong> {@code V2} creates a
 * <em>partial</em> unique index ({@code WHERE external_id IS NOT NULL}), and PostgreSQL will only
 * infer a partial index if the {@code ON CONFLICT} clause restates that predicate. Omitting it fails
 * at runtime with "there is no unique or exclusion constraint matching the ON CONFLICT
 * specification" — never at compile time (022 research R10).
 *
 * <p><strong>Why one statement rather than a batch.</strong> {@code batchUpdate} discards returned
 * rows, so a multi-row {@code VALUES} list is the only way to get {@code RETURNING} back. It is also
 * a single round trip for a whole statement rather than one per row.
 *
 * <p>Duplicates <em>within</em> one call are handled too: {@code DO NOTHING} conflicts against rows
 * inserted earlier in the same command, not only against pre-existing ones. That matters for a
 * malformed statement that repeats a bank-supplied transaction id.
 *
 * <p><strong>Every column the entity reads must be listed here.</strong> Hand-written SQL does not
 * get the compiler's help: a column left out is not an error, it is a row that reads back wrong or
 * not at all. {@code income.recurring} maps to a primitive {@code boolean}, so omitting it wrote
 * NULL and every later read of that row threw — breaking account balances, budgets and savings
 * goals long after the import that caused it. Adding a column to {@code BillEntity} or
 * {@code IncomeEntity} means revisiting this class.
 */
@Service
public class IngestTransactionsPostgresAdapter implements IngestTransactionsPersistencePort {

    private static final String BILL_INSERT = """
            insert into bill (id, description, amount, time, category_id, account_id,
                              reversal, recorded_at, external_id, recurring_series_id)
            values %s
            on conflict (account_id, external_id) where external_id is not null do nothing
            returning id, external_id
            """;

    private static final String INCOME_INSERT = """
            insert into income (id, description, amount, time, account_id,
                                reversal, recurring, recorded_at, external_id,
                                recurring_series_id)
            values %s
            on conflict (account_id, external_id) where external_id is not null do nothing
            returning id, external_id
            """;

    private static final String BILL_ROW_PLACEHOLDERS =
            "(:id%1$d, :description%1$d, :amount%1$d, :time%1$d, :categoryId%1$d, :accountId%1$d, "
                    + "false, :recordedAt%1$d, :externalId%1$d, :recurringSeriesId%1$d)";

    /**
     * IncomeDto carries no category: feature 017's suggestion rule only ever looked at past bills,
     * and this port deliberately reproduces that behaviour rather than inventing a new one.
     */
    private static final String INCOME_ROW_PLACEHOLDERS =
            "(:id%1$d, :description%1$d, :amount%1$d, :time%1$d, :accountId%1$d, "
                    + "false, false, :recordedAt%1$d, :externalId%1$d, :recurringSeriesId%1$d)";

    private final NamedParameterJdbcTemplate jdbc;

    public IngestTransactionsPostgresAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> insertNew(Collection<BillDto> bills, Collection<IncomeDto> incomes) {
        Map<String, String> inserted = new HashMap<>();
        inserted.putAll(insertBills(bills));
        inserted.putAll(insertIncomes(incomes));
        return inserted;
    }

    private Map<String, String> insertBills(Collection<BillDto> bills) {
        if (bills.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> rows = new ArrayList<>(bills.size());
        int i = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (BillDto bill : bills) {
            rows.add(BILL_ROW_PLACEHOLDERS.formatted(i));
            params.addValue("id" + i, UUID.randomUUID());
            params.addValue("description" + i, bill.getDescription());
            params.addValue("amount" + i, bill.getAmount());
            params.addValue("time" + i, bill.getTime());
            params.addValue("categoryId" + i, bill.getCategoryId());
            params.addValue("accountId" + i, bill.getAccountId());
            // Stamped here for the same reason AddBillPostgresAdapter stamps it: every row carries a
            // true write-time timestamp (Principle V).
            params.addValue("recordedAt" + i, now);
            params.addValue("externalId" + i, bill.getExternalId());
            // Feature 023: the series that produced this row, when the app posted it rather than a
            // person or a statement. Null for every other origin — and a column omitted here is a
            // provenance that silently does not exist, which is exactly how it was first missed.
            params.addValue("recurringSeriesId" + i, bill.getRecurringSeriesId());
            i++;
        }
        return runReturningInsert(BILL_INSERT.formatted(String.join(", ", rows)), params);
    }

    private Map<String, String> insertIncomes(Collection<IncomeDto> incomes) {
        if (incomes.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> rows = new ArrayList<>(incomes.size());
        int i = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (IncomeDto income : incomes) {
            rows.add(INCOME_ROW_PLACEHOLDERS.formatted(i));
            params.addValue("id" + i, UUID.randomUUID());
            params.addValue("description" + i, income.getDescription());
            params.addValue("amount" + i, income.getAmount());
            params.addValue("time" + i, income.getTime());
            params.addValue("accountId" + i, income.getAccountId());
            params.addValue("recordedAt" + i, now);
            params.addValue("externalId" + i, income.getExternalId());
            params.addValue("recurringSeriesId" + i, income.getRecurringSeriesId());
            i++;
        }
        return runReturningInsert(INCOME_INSERT.formatted(String.join(", ", rows)), params);
    }

    private Map<String, String> runReturningInsert(String sql, MapSqlParameterSource params) {
        Map<String, String> inserted = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            inserted.put(rs.getString("external_id"), rs.getString("id"));
        });
        return inserted;
    }

    @Override
    public Set<String> existingIdentities(String accountId, Collection<String> candidateIdentities) {
        if (candidateIdentities.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("identities", candidateIdentities);

        Set<String> found = new HashSet<>();
        jdbc.query("""
                select external_id from bill
                 where account_id = :accountId and external_id in (:identities)
                union all
                select external_id from income
                 where account_id = :accountId and external_id in (:identities)
                """, params, rs -> {
            found.add(rs.getString("external_id"));
        });
        return found;
    }
}
