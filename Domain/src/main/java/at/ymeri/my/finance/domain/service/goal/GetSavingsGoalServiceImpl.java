package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.api.GetSavingsGoalService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class GetSavingsGoalServiceImpl implements GetSavingsGoalService {

    private final GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;
    private final GetAccountService getAccountService;

    public GetSavingsGoalServiceImpl(GetSavingsGoalPersistencePort getSavingsGoalPersistencePort,
                                      GetAccountService getAccountService) {
        this.getSavingsGoalPersistencePort = getSavingsGoalPersistencePort;
        this.getAccountService = getAccountService;
    }

    @Override
    public List<SavingsGoalStatusDto> getAll() {
        return getSavingsGoalPersistencePort.getAll().stream()
                .map(this::toStatus)
                .toList();
    }

    @Override
    public SavingsGoalStatusDto getById(String id) {
        SavingsGoalDto goal = getSavingsGoalPersistencePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Savings goal not found: " + id));
        return toStatus(goal);
    }

    private SavingsGoalStatusDto toStatus(SavingsGoalDto goal) {
        AccountDto account = getAccountService.getAccountById(goal.getAccountId());
        return SavingsGoalProgress.of(goal, account.getBalance());
    }
}
