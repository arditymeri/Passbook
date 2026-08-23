package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;

import java.util.List;
import java.util.UUID;

/**
 * Shared reversal mechanics for correcting and removing bills.
 *
 * <p>A reversal is an ordinary bill row with the <em>negated</em> amount and the <em>same</em>
 * category, account and time as the row it reverses. Keeping those three fields identical is what
 * lets every existing aggregation (monthly summary, category spend, budget status, account balance)
 * net a correction out to zero with no changes of its own — if the category or account drifts,
 * per-category and per-account totals break silently while the grand total still looks right.
 */
final class BillCorrections {

    private BillCorrections() {
    }

    static BillDto reversalOf(BillDto current) {
        BillDto reversal = new BillDto();
        reversal.setAmount(current.getAmount().negate());
        reversal.setDescription(current.getDescription());
        reversal.setTime(current.getTime());
        reversal.setCategoryId(current.getCategoryId());
        reversal.setAccountId(current.getAccountId());
        reversal.setCurrency(current.getCurrency());
        reversal.setCorrectsTransactionId(current.getId());
        reversal.setReversal(true);
        return reversal;
    }

    /**
     * A row that something already references has been corrected or removed by another request;
     * acting on it again would double-count. Surfaced to the API as a 409.
     */
    static void assertNotSuperseded(List<BillDto> all, UUID id, String label) {
        boolean superseded = all.stream()
                .anyMatch(b -> id.toString().equals(b.getCorrectsTransactionId()));
        if (superseded) {
            throw new IllegalStateException(
                    label + " has already been corrected or removed: " + id);
        }
    }
}
