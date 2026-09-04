package at.ymeri.my.finance.domain.data.ingestion;

/**
 * What happened to one row.
 *
 * @param rowIndex        position in the file, matching the {@link StatementRow} it came from.
 * @param status          see {@link RowStatus}.
 * @param rejectionReason present only when {@code status} is {@link RowStatus#REJECTED}.
 * @param transactionId   present only when {@code status} is {@link RowStatus#RECORDED}.
 */
public record RowOutcome(
        int rowIndex,
        RowStatus status,
        String rejectionReason,
        String transactionId) {

    public static RowOutcome recorded(int rowIndex, String transactionId) {
        return new RowOutcome(rowIndex, RowStatus.RECORDED, null, transactionId);
    }

    public static RowOutcome alreadyRecorded(int rowIndex) {
        return new RowOutcome(rowIndex, RowStatus.ALREADY_RECORDED, null, null);
    }

    public static RowOutcome rejected(int rowIndex, String reason) {
        return new RowOutcome(rowIndex, RowStatus.REJECTED, reason, null);
    }

    public static RowOutcome excluded(int rowIndex) {
        return new RowOutcome(rowIndex, RowStatus.EXCLUDED, null, null);
    }
}
