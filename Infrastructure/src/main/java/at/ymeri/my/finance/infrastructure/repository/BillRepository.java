package at.ymeri.my.finance.infrastructure.repository;


import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillRepository extends JpaRepository<BillEntity, UUID> {

    boolean existsByCategoryId(String categoryId);

    boolean existsByAccountId(String accountId);
}
