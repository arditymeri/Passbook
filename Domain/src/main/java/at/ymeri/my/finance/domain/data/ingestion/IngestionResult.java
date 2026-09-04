package at.ymeri.my.finance.domain.data.ingestion;

import java.util.List;

/**
 * Per-row outcomes of an ingestion plus the totals an operator reads to know what just happened
 * (FR-011). Transient; never persisted.
 */
public record IngestionResult(List<RowOutcome> rows) {

    public long countOf(RowStatus status) {
        return rows.stream().filter(row -> row.status() == status).count();
    }

    public long recordedCount() {
        return countOf(RowStatus.RECORDED);
    }

    public long alreadyRecordedCount() {
        return countOf(RowStatus.ALREADY_RECORDED);
    }

    public long rejectedCount() {
        return countOf(RowStatus.REJECTED);
    }

    public long excludedCount() {
        return countOf(RowStatus.EXCLUDED);
    }
}
