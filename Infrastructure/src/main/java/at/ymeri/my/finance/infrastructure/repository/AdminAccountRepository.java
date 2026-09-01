package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {

    /**
     * At most one row ever exists (enforced in {@code SetupAdminAccountServiceImpl}, not by a DB
     * constraint) — {@code findAll().stream().findFirst()} would work equally well, but a direct
     * top-1 query avoids loading a whole (single-row) table unnecessarily.
     */
    AdminAccountEntity findFirstByOrderByCreatedAtAsc();
}
