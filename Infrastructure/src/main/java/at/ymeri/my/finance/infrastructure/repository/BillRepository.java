package at.ymeri.my.finance.infrastructure.repository;


import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<BillEntity, UUID> {

    /**
     * SELECT ... FOR UPDATE. Serializes concurrent corrections/removals of the same row so
     * only one can pass the "not yet superseded" check. Needs a surrounding transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from BillEntity e where e.id = :id")
    Optional<BillEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCategoryId(String categoryId);

    boolean existsByAccountId(String accountId);

    List<BillEntity> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * Auto-posted rows on one account that are not themselves reversals — the candidates an
     * imported transaction could supersede. Reads through the {@code recurring_series_id} index
     * added in {@code V3} rather than scanning the ledger, which reconciliation would otherwise do
     * on every import.
     */
    List<BillEntity> findByAccountIdAndRecurringSeriesIdNotNullAndReversalFalse(String accountId);

    /**
     * Reversals on one account. Their {@code correctsTransactionId} values are the rows already
     * superseded, which FR-010 makes ineligible for superseding again.
     */
    List<BillEntity> findByAccountIdAndCorrectsTransactionIdNotNull(String accountId);
}
