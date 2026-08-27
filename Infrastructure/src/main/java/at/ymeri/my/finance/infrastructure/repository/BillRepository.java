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
}
