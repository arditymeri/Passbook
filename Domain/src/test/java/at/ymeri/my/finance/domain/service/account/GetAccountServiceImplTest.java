package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountServiceImplTest {

    @Mock
    private GetAccountPersistencePort getAccountPersistencePort;

    @Mock
    private GetBillPersistencePort getBillPersistencePort;

    @Mock
    private GetIncomePersistencePort getIncomePersistencePort;

    @InjectMocks
    private GetAccountServiceImpl service;

    @Test
    void getAccountById_noLinkedTransactions_returnsStartingBalanceUnchanged() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of());
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(new BigDecimal("1000.00"), result.getBalance());
    }

    @Test
    void getAccountById_nullStartingBalance_treatedAsZero() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", null)));
        when(getBillPersistencePort.getAll()).thenReturn(List.of());
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(BigDecimal.ZERO, result.getBalance());
    }

    @Test
    void getAccountById_linkedIncomesOnly_addsToBalance() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of());
        when(getIncomePersistencePort.getAll())
                .thenReturn(List.of(income("acc-1", new BigDecimal("200.00")), income("acc-1", new BigDecimal("50.00"))));

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(new BigDecimal("1250.00"), result.getBalance());
    }

    @Test
    void getAccountById_linkedBillsOnly_subtractsFromBalance() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(bill("acc-1", new BigDecimal("50.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(new BigDecimal("950.00"), result.getBalance());
    }

    @Test
    void getAccountById_mixedBillsAndIncomes_computesNetBalance() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(bill("acc-1", new BigDecimal("50.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(income("acc-1", new BigDecimal("200.00"))));

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(new BigDecimal("1150.00"), result.getBalance());
    }

    @Test
    void getAccountById_transactionsForOtherAccount_excludedFromBalance() {
        when(getAccountPersistencePort.getAccountById("acc-1"))
                .thenReturn(Optional.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(bill("acc-2", new BigDecimal("999.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(income("acc-2", new BigDecimal("999.00"))));

        AccountDto result = service.getAccountById("acc-1");

        assertEquals(new BigDecimal("1000.00"), result.getBalance());
    }

    @Test
    void getAll_derivesBalanceForEveryAccount() {
        when(getAccountPersistencePort.getAll())
                .thenReturn(List.of(account("acc-1", new BigDecimal("1000.00")), account("acc-2", new BigDecimal("500.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(bill("acc-1", new BigDecimal("100.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of(income("acc-2", new BigDecimal("25.00"))));

        List<AccountDto> result = service.getAll();

        assertEquals(new BigDecimal("900.00"), result.get(0).getBalance());
        assertEquals(new BigDecimal("525.00"), result.get(1).getBalance());
    }

    @Test
    void getByType_derivesBalanceForEveryAccount() {
        when(getAccountPersistencePort.getByType("CHECKING"))
                .thenReturn(List.of(account("acc-1", new BigDecimal("1000.00"))));
        when(getBillPersistencePort.getAll()).thenReturn(List.of(bill("acc-1", new BigDecimal("50.00"))));
        when(getIncomePersistencePort.getAll()).thenReturn(List.of());

        List<AccountDto> result = service.getByType("CHECKING");

        assertEquals(new BigDecimal("950.00"), result.get(0).getBalance());
    }

    private AccountDto account(String id, BigDecimal startingBalance) {
        AccountDto dto = new AccountDto();
        dto.setId(id);
        dto.setName("Account " + id);
        dto.setType(AccountType.CHECKING);
        dto.setCurrencies(List.of("EUR"));
        dto.setDefaultCurrency("EUR");
        dto.setBalance(startingBalance);
        return dto;
    }

    private BillDto bill(String accountId, BigDecimal amount) {
        BillDto dto = new BillDto();
        dto.setAccountId(accountId);
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.now());
        return dto;
    }

    private IncomeDto income(String accountId, BigDecimal amount) {
        IncomeDto dto = new IncomeDto();
        dto.setAccountId(accountId);
        dto.setAmount(amount);
        dto.setTime(OffsetDateTime.now());
        return dto;
    }
}
