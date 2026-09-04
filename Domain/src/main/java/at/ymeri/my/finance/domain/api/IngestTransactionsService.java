package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.ingestion.IngestionResult;
import at.ymeri.my.finance.domain.data.ingestion.StatementRow;

import java.util.List;
import java.util.Set;

/**
 * Records statement rows, exactly once each.
 *
 * <p>Takes already-structured rows rather than a file, so any producer can use it — the file upload
 * path today, a Kafka consumer later (FR-016).
 */
public interface IngestTransactionsService {

    /**
     * Writes every row that is neither rejected nor excluded, and reports per row what happened.
     *
     * <p>Whether a row was newly recorded or already present is decided by the write itself, never
     * by looking first: two imports of overlapping statements running at the same moment would both
     * look, both see nothing, and both write (FR-005).
     *
     * @param excludedRowIndexes zero-based positions the operator chose not to import. Nothing is
     *                           recorded about an exclusion, so the same row is offered again as new
     *                           on a later import of the same statement (FR-014).
     */
    IngestionResult ingest(List<StatementRow> rows, String accountId, Set<Integer> excludedRowIndexes);
}
