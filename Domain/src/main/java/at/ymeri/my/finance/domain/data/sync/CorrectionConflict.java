package at.ymeri.my.finance.domain.data.sync;

/**
 * Two sibling corrections of the same original transaction (bill or income), only ever possible
 * once sync introduces a second device's history into the mix (research.md R3) — a single device
 * never lets more than one exist via {@code assertNotSuperseded}. {@code winningId} is the one
 * with the later {@code recordedAt}, kept as the transaction's current value; {@code losingId}
 * remains permanently visible in that transaction's correction history (Constitution Principle
 * I), never deleted.
 */
public record CorrectionConflict(String correctsTransactionId, String winningId, String losingId) {
}
