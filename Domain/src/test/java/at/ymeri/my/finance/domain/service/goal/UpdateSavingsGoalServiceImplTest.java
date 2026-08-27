package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.UpdateSavingsGoalPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSavingsGoalServiceImplTest {

    @Mock
    private GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    @Mock
    private UpdateSavingsGoalPersistencePort updateSavingsGoalPersistencePort;

    @Mock
    private GetAccountService getAccountService;

    @InjectMocks
    private UpdateSavingsGoalServiceImpl service;

    @Test
    void updateGoal_validRequest_returnsRefreshedStatus() {
        SavingsGoalDto existing = goal("goal-1", "account-1", "Old Name", new BigDecimal("500"));
        when(getSavingsGoalPersistencePort.findById("goal-1")).thenReturn(Optional.of(existing));
        when(updateSavingsGoalPersistencePort.update(eq("goal-1"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", new BigDecimal("200")));

        SavingsGoalStatusDto result = service.updateGoal("goal-1", "New Name", new BigDecimal("800"), null);

        assertEquals("New Name", result.getName());
        assertEquals(0, new BigDecimal("800").compareTo(result.getTargetAmount()));
        assertEquals(0, new BigDecimal("200").compareTo(result.getSavedAmount()));
    }

    @Test
    void updateGoal_validRequest_accountIdUnchanged() {
        SavingsGoalDto existing = goal("goal-1", "account-1", "Old Name", new BigDecimal("500"));
        when(getSavingsGoalPersistencePort.findById("goal-1")).thenReturn(Optional.of(existing));
        when(updateSavingsGoalPersistencePort.update(eq("goal-1"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", BigDecimal.ZERO));

        service.updateGoal("goal-1", "New Name", new BigDecimal("800"), null);

        ArgumentCaptor<SavingsGoalDto> captor = ArgumentCaptor.forClass(SavingsGoalDto.class);
        org.mockito.Mockito.verify(updateSavingsGoalPersistencePort).update(eq("goal-1"), captor.capture());
        assertEquals("account-1", captor.getValue().getAccountId());
    }

    @Test
    void updateGoal_targetAmountZeroOrNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateGoal("goal-1", "Name", BigDecimal.ZERO, null));
    }

    @Test
    void updateGoal_unknownId_throwsNoSuchElement() {
        when(getSavingsGoalPersistencePort.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.updateGoal("missing", "Name", new BigDecimal("100"), null));
    }

    private SavingsGoalDto goal(String id, String accountId, String name, BigDecimal targetAmount) {
        SavingsGoalDto dto = new SavingsGoalDto();
        dto.setId(id);
        dto.setName(name);
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
