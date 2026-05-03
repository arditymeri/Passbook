package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddAccountServiceImplTest {

    @Mock
    private AddAccountPersistencePort addAccountPersistencePort;

    @Mock
    private GetAccountPersistencePort getAccountPersistencePort;

    @InjectMocks
    private AddAccountServiceImpl addAccountService;

    @Test
    void addAccount_validRequest_returnsSavedAccountWithId() {
        AccountDto request = validAccount();
        AccountDto saved = validAccount();
        saved.setId("generated-id");
        when(getAccountPersistencePort.existsByName("ING Checking")).thenReturn(false);
        when(addAccountPersistencePort.addAccount(request)).thenReturn(saved);

        AccountDto result = addAccountService.addAccount(request);

        assertThat(result.getId()).isEqualTo("generated-id");
        verify(addAccountPersistencePort).addAccount(request);
    }

    @Test
    void addAccount_nullName_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setName(null);

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required");
    }

    @Test
    void addAccount_blankName_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setName("  ");

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name must not be blank");
    }

    @Test
    void addAccount_nullType_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setType(null);

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type is required");
    }

    @Test
    void addAccount_emptyCurrencies_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setCurrencies(List.of());

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one currency is required");
    }

    @Test
    void addAccount_nullCurrencies_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setCurrencies(null);

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one currency is required");
    }

    @Test
    void addAccount_invalidIsoCurrencyCode_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setCurrencies(List.of("EUR", "XYZ"));

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ISO 4217 currency code: XYZ");
    }

    @Test
    void addAccount_defaultCurrencyNotInList_throwsIllegalArgumentException() {
        AccountDto account = validAccount();
        account.setDefaultCurrency("USD");

        assertThatThrownBy(() -> addAccountService.addAccount(account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Default currency must be one of the account currencies");
    }

    @Test
    void addAccount_duplicateName_throwsIllegalStateException() {
        AccountDto account = validAccount();
        when(getAccountPersistencePort.existsByName("ING Checking")).thenReturn(true);

        assertThatThrownBy(() -> addAccountService.addAccount(account))
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
