package at.ymeri.my.finance.domain.data.sync;

/**
 * One entity to update: {@code localId} is the id of the row already stored locally (which, for
 * a natural-key match, may differ from {@code incoming}'s own id — research.md R2), and
 * {@code incoming} carries the new field values to write.
 */
public record MergeUpdate<T>(String localId, T incoming) {
}
