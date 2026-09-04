package at.ymeri.my.finance.domain.spi.recurring;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.List;

/**
 * Finds the auto-posted transactions that an imported one could supersede.
 *
 * <p><strong>Why this is its own port rather than a filter over "get everything".</strong>
 * Reconciliation runs on every import, and an operator importing a year of statements runs it once
 * per file. Scanning the whole ledger each time works and is what the first cut did; querying the
 * indexed {@code recurring_series_id} column added in {@code V3} is what that index is for.
 *
 * <p>FR-010 — an entry that already has a reversal referencing it is not eligible — is answered
 * here rather than by the caller, so every caller gets it and none can forget to.
 */
public interface GetAutoPostedTransactionsPersistencePort {

    /**
     * Auto-posted bills on one account that nothing has superseded yet, and that are not themselves
     * reversals.
     */
    List<BillDto> supersedableBills(String accountId);

    /** The same, for income. */
    List<IncomeDto> supersedableIncomes(String accountId);
}
