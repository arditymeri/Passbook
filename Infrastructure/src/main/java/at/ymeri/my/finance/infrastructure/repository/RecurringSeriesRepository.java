package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.RecurringSeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecurringSeriesRepository extends JpaRepository<RecurringSeriesEntity, UUID> {

    Optional<RecurringSeriesEntity> findByTransactionTypeAndGroupKeyAndDescription(
            String transactionType, String groupKey, String description);
}
