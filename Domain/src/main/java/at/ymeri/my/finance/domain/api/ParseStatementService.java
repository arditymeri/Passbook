package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.ingestion.StatementRow;

import java.util.List;

/**
 * Turns statement text into structured rows, each already carrying the identity it would be stored
 * under.
 *
 * <p>Kept separate from {@link IngestTransactionsService} on purpose (FR-016): a future caller with
 * already-structured input — the Kafka {@code BookingConsumer}, which today logs its message and
 * returns — needs ingestion without parsing, and should not have to fabricate CSV or force a
 * redesign to get it.
 */
public interface ParseStatementService {

    /**
     * Parses every row, in file order. Rows that cannot be used are returned as rejected with a
     * reason rather than dropped (FR-011) — a bad row must not silently vanish, and must not stop
     * the rows around it.
     *
     * @throws IllegalArgumentException if the input is not a readable statement at all, in which
     *                                  case nothing should be recorded (FR-015)
     */
    List<StatementRow> parse(String csvText, String accountId);
}
