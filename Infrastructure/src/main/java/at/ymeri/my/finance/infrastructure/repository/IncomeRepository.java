package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncomeRepository extends JpaRepository<IncomeEntity, UUID> {
}
