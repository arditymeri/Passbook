package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;
import java.util.UUID;

/**
 * Shared reversal mechanics for correcting and removing incomes.
 *
 * <p>A reversal is an ordinary income row with the <em>negated</em> amount and the <em>same</em>
 * source, account and time as the row it reverses. Keeping those three fields identical is what
 * lets every existing aggregation (monthly summary, account balance) net a correction out to zero
 * with no changes of its own.
 */
final class IncomeCorrections {

    private IncomeCorrections() {
    }

    static IncomeDto reversalOf(IncomeDto current) {
        IncomeDto reversal = new IncomeDto();
        reversal.setAmount(current.getAmount().negate());
        reversal.setDescription(current.getDescription());
        reversal.setTime(current.getTime());
        reversal.setSource(current.getSource());
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
    static void assertNotSuperseded(List<IncomeDto> all, UUID id, String label) {
        boolean superseded = all.stream()
                .anyMatch(i -> id.toString().equals(i.getCorrectsTransactionId()));
        if (superseded) {
            throw new IllegalStateException(
                    label + " has already been corrected or removed: " + id);
        }
    }
}
