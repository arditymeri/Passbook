package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.AddSavingsGoalService;
import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.AddSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class AddSavingsGoalServiceImpl implements AddSavingsGoalService {

    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;
    private final AddSavingsGoalPersistencePort addSavingsGoalPersistencePort;
    private final GetAccountService getAccountService;

    public AddSavingsGoalServiceImpl(GetSavingsGoalPersistencePort getSavingsGoalPersistencePort,
                                      AddSavingsGoalPersistencePort addSavingsGoalPersistencePort,
                                      GetAccountService getAccountService) {
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
        this.addSavingsGoalPersistencePort = addSavingsGoalPersistencePort;
        this.getAccountService = getAccountService;
    }

    @Override
    public SavingsGoalStatusDto addGoal(String name, BigDecimal targetAmount, OffsetDateTime targetDate, String accountId) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetAmount must be greater than zero");
        }

        AccountDto account = getAccountService.getAccountById(accountId);

        if (getSavingsGoalPersistencePort.findByAccountId(accountId).isPresent()) {
            throw new IllegalStateException("Account " + accountId + " already funds another goal");
        }

        SavingsGoalDto goal = new SavingsGoalDto();
        goal.setName(name);
        goal.setTargetAmount(targetAmount);
        goal.setTargetDate(targetDate);
        goal.setAccountId(accountId);
        goal.setCreatedAt(OffsetDateTime.now());

        SavingsGoalDto saved = addSavingsGoalPersistencePort.add(goal);
        return SavingsGoalProgress.of(saved, account.getBalance());
    }
}
