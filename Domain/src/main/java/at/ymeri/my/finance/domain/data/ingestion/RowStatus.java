package at.ymeri.my.finance.domain.data.ingestion;

/**
 * What happened, or would happen, to one statement row.
 *
 * <p>On an ingest, {@link #RECORDED} and {@link #ALREADY_RECORDED} are determined by the write
 * itself — the rows the database reported back as inserted — never by a lookup, which could disagree
 * with the write under concurrency. On a preview the same two values come from a read and are
 * explicitly advisory (022 research R3, R7).
 */
public enum RowStatus {
    /** Newly written by this request. */
    RECORDED,
    /** Its identity was already present; nothing was written. */
    ALREADY_RECORDED,
    /** Unusable; carries a reason. Does not block the rows around it. */
    REJECTED,
    /** The operator chose not to import it. Nothing is recorded about the exclusion. */
    EXCLUDED
}
