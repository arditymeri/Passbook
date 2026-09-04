package at.ymeri.my.finance.domain.data.ingestion;

/**
 * Which side of the ledger a statement row belongs to.
 *
 * <p>Part of a row's derived identity, not merely a routing decision: {@code bill} and {@code income}
 * both store positive amounts, so without direction in the hash a refund would collide with the
 * charge it reverses — an entirely ordinary pattern, since both usually carry the same merchant
 * string (022 research R1).
 */
public enum TransactionDirection {
    BILL,
    INCOME
}
