package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSavingsGoalServiceImplTest {

    @Mock
    private GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    @Mock
    private GetAccountService getAccountService;

    @InjectMocks
    private GetSavingsGoalServiceImpl service;

    @Test
    void getAll_goalBelowTarget_reflectsAccountBalance() {
        SavingsGoalDto goal = goal("goal-1", "account-1", new BigDecimal("1000"));
        when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of(goal));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", new BigDecimal("300")));

        List<SavingsGoalStatusDto> result = service.getAll();

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("300").compareTo(result.get(0).getSavedAmount()));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.get(0).getPercentComplete()));
        assertEquals(0, new BigDecimal("700").compareTo(result.get(0).getRemainingAmount()));
        assertFalse(result.get(0).isAchieved());
    }

    @Test
    void getAll_negativeAccountBalance_percentFlooredAtZero() {
        SavingsGoalDto goal = goal("goal-1", "account-1", new BigDecimal("1000"));
        when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of(goal));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", new BigDecimal("-100")));

        List<SavingsGoalStatusDto> result = service.getAll();

        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getPercentComplete()));
    }

    @Test
    void getAll_balanceAtOrAboveTarget_marksAchieved() {
        SavingsGoalDto goal = goal("goal-1", "account-1", new BigDecimal("1000"));
        when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of(goal));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", new BigDecimal("1200")));

        List<SavingsGoalStatusDto> result = service.getAll();

        assertTrue(result.get(0).isAchieved());
    }

    @Test
    void getAll_noGoals_returnsEmptyList() {
        when(getSavingsGoalPersistencePort.getAll()).thenReturn(List.of());

        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void getById_existingGoal_returnsDerivedStatus() {
        SavingsGoalDto goal = goal("goal-1", "account-1", new BigDecimal("1000"));
        when(getSavingsGoalPersistencePort.findById("goal-1")).thenReturn(Optional.of(goal));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", new BigDecimal("500")));

        SavingsGoalStatusDto result = service.getById("goal-1");

        assertEquals("goal-1", result.getId());
        assertEquals(0, new BigDecimal("500").compareTo(result.getSavedAmount()));
    }

    @Test
    void getById_unknownId_throwsNoSuchElement() {
        when(getSavingsGoalPersistencePort.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getById("missing"));
    }

    private SavingsGoalDto goal(String id, String accountId, BigDecimal targetAmount) {
        SavingsGoalDto dto = new SavingsGoalDto();
        dto.setId(id);
        dto.setName("Vacation Fund");
        dto.setTargetAmount(targetAmount);
        dto.setAccountId(accountId);
        dto.setCreatedAt(OffsetDateTime.now().minusMonths(1));
        return dto;
    }

    private AccountDto account(String id, BigDecimal balance) {
        AccountDto dto = new AccountDto();
        dto.setId(id);
        dto.setBalance(balance);
        return dto;
    }
}
