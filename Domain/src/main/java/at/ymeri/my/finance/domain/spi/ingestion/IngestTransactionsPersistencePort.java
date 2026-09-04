package at.ymeri.my.finance.domain.spi.ingestion;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Writes ingested transactions, exactly once each.
 *
 * <p><strong>The shape of {@link #insertNew} is the point.</strong> It is "insert these and tell me
 * which landed", not "does this exist?" followed by a write. The second shape is the obvious one and
 * it is wrong: two imports of overlapping statements running at the same moment would both look,
 * both see nothing, and both write. Uniqueness has to be arbitrated by the store (FR-005), so the
 * only honest thing the port can report is what the write itself did.
 */
public interface IngestTransactionsPersistencePort {

    /**
     * Inserts every given transaction whose external identity is not already present, and skips the
     * rest without modifying them.
     *
     * @return external identity to the id of the transaction created for it, containing an entry
     *         <em>only</em> for rows that were actually inserted. Anything submitted and absent from
     *         this map was already recorded.
     */
    Map<String, String> insertNew(Collection<BillDto> bills, Collection<IncomeDto> incomes);

    /**
     * Which of these identities already exist on this account.
     *
     * <p><strong>Advisory only — for building a preview.</strong> Never use this to decide whether
     * to write: by the time a caller acted on the answer it could be stale, which is exactly the
     * race {@link #insertNew} exists to avoid.
     */
    Set<String> existingIdentities(String accountId, Collection<String> candidateIdentities);
}
