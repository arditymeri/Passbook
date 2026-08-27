package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.AllocationTransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AllocationTransferRepository extends JpaRepository<AllocationTransferEntity, UUID> {
}
