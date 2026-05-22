package at.ymeri.my.finance.infrastructure.repository;

import at.ymeri.my.finance.infrastructure.entity.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

    List<BudgetEntity> findByYearAndMonth(int year, int month);

    Optional<BudgetEntity> findByCategoryIdAndYearAndMonth(String categoryId, int year, int month);
}
