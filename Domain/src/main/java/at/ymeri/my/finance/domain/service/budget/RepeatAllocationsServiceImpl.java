package at.ymeri.my.finance.domain.service.budget;

import at.ymeri.my.finance.domain.api.RepeatAllocationsService;
import at.ymeri.my.finance.domain.data.budget.AllocationTopUp;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.spi.budget.GetBudgetPersistencePort;
import at.ymeri.my.finance.domain.spi.budget.SetBudgetPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RepeatAllocationsServiceImpl implements RepeatAllocationsService {

    private final GetBudgetPersistencePort getBudgetPersistencePort;
    private final SetBudgetPersistencePort setBudgetPersistencePort;

    public RepeatAllocationsServiceImpl(GetBudgetPersistencePort getBudgetPersistencePort,
                                         SetBudgetPersistencePort setBudgetPersistencePort) {
        this.getBudgetPersistencePort = getBudgetPersistencePort;
        this.setBudgetPersistencePort = setBudgetPersistencePort;
    }

    @Override
    public List<AllocationTopUp> repeatAllocations(int fromYear, int fromMonth, int toYear, int toMonth) {
        List<BudgetDto> source = getBudgetPersistencePort.findByYearAndMonth(fromYear, fromMonth);
        if (source.isEmpty()) {
            throw new IllegalArgumentException(
                    "Source month " + fromYear + "-" + fromMonth + " has no allocations to repeat");
        }

        List<AllocationTopUp> applied = new ArrayList<>();
        for (BudgetDto sourceAllocation : source) {
            Optional<BudgetDto> existingTarget = getBudgetPersistencePort
                    .findByCategoryIdAndYearAndMonth(sourceAllocation.getCategoryId(), toYear, toMonth);
            BigDecimal existingAmount = existingTarget.map(BudgetDto::getLimitAmount).orElse(BigDecimal.ZERO);
            BigDecimal newAmount = existingAmount.add(sourceAllocation.getLimitAmount());

            BudgetDto toUpsert = existingTarget.orElseGet(BudgetDto::new);
            toUpsert.setCategoryId(sourceAllocation.getCategoryId());
            toUpsert.setYear(toYear);
            toUpsert.setMonth(toMonth);
            toUpsert.setLimitAmount(newAmount);
            setBudgetPersistencePort.upsert(toUpsert);

            AllocationTopUp topUp = new AllocationTopUp();
            topUp.setCategoryId(sourceAllocation.getCategoryId());
            topUp.setAmountAdded(sourceAllocation.getLimitAmount());
            topUp.setNewMonthlyAmount(newAmount);
            applied.add(topUp);
        }

        return applied;
    }
}
