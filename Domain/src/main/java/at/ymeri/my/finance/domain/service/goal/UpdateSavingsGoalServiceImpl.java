package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.UpdateSavingsGoalService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.UpdateSavingsGoalPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class UpdateSavingsGoalServiceImpl implements UpdateSavingsGoalService {

    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;
    private final UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort;
    private final GetAccountService getAccountService;

    public UpdateSavingsGoalServiceImpl(GetSavingsGoalPersistencePort getSavingsGoalPersistencePort,
                                         UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort,
                                         GetAccountService getAccountService) {
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
        this.updateSavingsGoalPersistencePort = updateSavingsGoalPersistencePort;
        this.getAccountService = getAccountService;
    }

    @Override
    public SavingsGoalStatusDto updateGoal(String id, String name, BigDecimal targetAmount, OffsetDateTime targetDate) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetAmount must be greater than zero");
        }

        SavingsGoalDto existing = getSavingsGoalPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Savings goal not found: " + id));

        SavingsGoalDto updated = new SavingsGoalDto();
        updated.setId(existing.getId());
        updated.setName(name);
        updated.setTargetAmount(targetAmount);
        updated.setTargetDate(targetDate);
        updated.setAccountId(existing.getAccountId());
        updated.setCreatedAt(existing.getCreatedAt());

        SavingsGoalDto saved = updateSavingsGoalPersistencePort.update(id, updated);
        AccountDto account = getAccountService.getAccountById(saved.getAccountId());
        return SavingsGoalProgress.of(saved, account.getBalance());
    }
}
