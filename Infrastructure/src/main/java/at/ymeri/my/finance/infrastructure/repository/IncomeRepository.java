package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<IncomeEntity, UUID> {

    boolean existsByCategoryId(String categoryId);

    boolean existsByAccountId(String accountId);

    List<IncomeEntity> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
