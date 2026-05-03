package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.UpdateAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAccountServiceImplTest {

    @Mock
    private UpdateAccountPersistencePort updateAccountPersistencePort;

    @Mock
    private GetAccountPersistencePort getAccountPersistencePort;

    @InjectMocks
    private UpdateAccountServiceImpl updateAccountService;

    @Test
    void updateAccount_validRequest_returnsUpdatedAccount() {
        AccountDto request = validAccount();
        AccountDto existing = validAccount();
        existing.setId("id-1");
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(existing));
        when(getAccountPersistencePort.existsByNameAndIdNot("ING Checking", "id-1")).thenReturn(false);
        when(updateAccountPersistencePort.updateAccount("id-1", request)).thenReturn(existing);

        AccountDto result = updateAccountService.updateAccount("id-1", request);

        assertThat(result.getId()).isEqualTo("id-1");
        verify(updateAccountPersistencePort).updateAccount("id-1", request);
    }

    @Test
    void updateAccount_notFound_throwsNoSuchElementException() {
        when(getAccountPersistencePort.getAccountById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateAccountService.updateAccount("missing", validAccount()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateAccount_blankName_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setName("  ");
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> updateAccountService.updateAccount("id-1", account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name must not be blank");
    }

    @Test
    void updateAccount_invalidCurrencyCode_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setCurrencies(List.of("XYZ"));
        account.setDefaultCurrency("XYZ");
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> updateAccountService.updateAccount("id-1", account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ISO 4217 currency code: XYZ");
    }

    @Test
    void updateAccount_defaultCurrencyNotInList_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setDefaultCurrency("USD");
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> updateAccountService.updateAccount("id-1", account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Default currency must be one of the account currencies");
    }

    @Test
    void updateAccount_nameTakenByOtherAccount_throwsIllegalStateException() {
        AccountDto account = validAccount();
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(account));
        when(getAccountPersistencePort.existsByNameAndIdNot("ING Checking", "id-1")).thenReturn(true);

        assertThatThrownBy(() -> updateAccountService.updateAccount("id-1", account))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account name already exists: ING Checking");
    }

    private AccountDto validAccount() {
        AccountDto dto = new AccountDto();
        dto.setName("ING Checking");
        dto.setType(AccountType.CHECKING);
        dto.setCurrencies(List.of("EUR"));
        dto.setDefaultCurrency("EUR");
        return dto;
    }
}
