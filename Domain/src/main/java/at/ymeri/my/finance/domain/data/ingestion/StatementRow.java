package at.ymeri.my.finance.domain.data.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One parsed row of a statement, before anything is written. Transient — no statement, and no record
 * of which files have been seen, is ever persisted: idempotency is a property of the transactions
 * themselves (022 data-model §2).
 *
 * @param rowIndex            position in the file, zero-based. Stable across re-parses of the same
 *                            file, and how the operator names an exclusion on ingest.
 * @param date                calendar date from the statement. Identity uses this rather than a
 *                            timestamp, because any time-of-day is invented by the parser rather
 *                            than stated by the statement.
 * @param description         merchant or reference text. May be empty, which is legal — such rows
 *                            simply share an identity group and are separated by occurrence index.
 * @param amount              always positive (Principle IV: {@code BigDecimal}, never floating
 *                            point). {@code direction} carries the sign.
 * @param direction           which side of the ledger this row belongs to.
 * @param sourceTransactionId the bank's own identifier, when the statement supplies one. Preferred
 *                            over the derived form, and immune to the statement-boundary edge case
 *                            in 022 research R2.
 * @param externalId          the identity this row will be stored under; derived during parsing.
 * @param rejectionReason     non-null exactly when this row is unusable.
 */
public record StatementRow(
        int rowIndex,
        LocalDate date,
        String description,
        BigDecimal amount,
        TransactionDirection direction,
        String sourceTransactionId,
        String externalId,
        String rejectionReason) {

    public boolean isRejected() {
        return rejectionReason != null;
    }
}
