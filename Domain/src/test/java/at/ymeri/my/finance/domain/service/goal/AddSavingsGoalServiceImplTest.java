package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import at.ymeri.my.finance.domain.spi.goal.AddSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddSavingsGoalServiceImplTest {

    @Mock
    private GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    @Mock
    private AddSavingsGoalPersistencePort addSavingsGoalPersistencePort;

    @Mock
    private GetAccountService getAccountService;

    @InjectMocks
    private AddSavingsGoalServiceImpl service;

    @Test
    void addGoal_validRequest_createsAndReturnsDerivedProgress() {
        AccountDto account = account("account-1", new BigDecimal("250"));
        when(getAccountService.getAccountById("account-1")).thenReturn(account);
        lenient().when(getSavingsGoalPersistencePort.findByAccountId("account-1")).thenReturn(Optional.empty());
        when(addSavingsGoalPersistencePort.add(any())).thenAnswer(invocation -> {
            SavingsGoalDto dto = invocation.getArgument(0);
            dto.setId("goal-1");
            return dto;
        });

        SavingsGoalStatusDto result = service.addGoal("Vacation Fund", new BigDecimal("1000"), null, "account-1");

        assertEquals("goal-1", result.getId());
        assertEquals("Vacation Fund", result.getName());
        assertEquals(0, new BigDecimal("1000").compareTo(result.getTargetAmount()));
        assertEquals("account-1", result.getAccountId());
        assertEquals(0, new BigDecimal("250").compareTo(result.getSavedAmount()));
    }

    @Test
    void addGoal_validRequest_persistsWithGeneratedCreatedAt() {
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", BigDecimal.ZERO));
        lenient().when(getSavingsGoalPersistencePort.findByAccountId("account-1")).thenReturn(Optional.empty());
        when(addSavingsGoalPersistencePort.add(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addGoal("Vacation Fund", new BigDecimal("1000"), null, "account-1");

        ArgumentCaptor<SavingsGoalDto> captor = ArgumentCaptor.forClass(SavingsGoalDto.class);
        verify(addSavingsGoalPersistencePort).add(captor.capture());
        assertEquals("Vacation Fund", captor.getValue().getName());
        assertEquals("account-1", captor.getValue().getAccountId());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void addGoal_targetAmountZeroOrNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addGoal("Vacation Fund", BigDecimal.ZERO, null, "account-1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.addGoal("Vacation Fund", new BigDecimal("-5"), null, "account-1"));
    }

    @Test
    void addGoal_unknownAccount_throwsNoSuchElement() {
        when(getAccountService.getAccountById("missing")).thenThrow(new NoSuchElementException());

        assertThrows(NoSuchElementException.class,
                () -> service.addGoal("Vacation Fund", new BigDecimal("1000"), null, "missing"));
    }

    @Test
    void addGoal_accountAlreadyLinkedToAnotherGoal_throwsIllegalState() {
        when(getAccountService.getAccountById("account-1")).thenReturn(account("account-1", BigDecimal.ZERO));
        SavingsGoalDto existing = new SavingsGoalDto();
        existing.setId("goal-existing");
        existing.setAccountId("account-1");
        when(getSavingsGoalPersistencePort.findByAccountId("account-1")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> service.addGoal("Vacation Fund", new BigDecimal("1000"), null, "account-1"));
    }

    private AccountDto account(String id, BigDecimal balance) {
        AccountDto dto = new AccountDto();
        dto.setId(id);
        dto.setBalance(balance);
        return dto;
    }
}
