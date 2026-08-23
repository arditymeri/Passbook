package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.domain.spi.account.DeleteAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteAccountServiceImplTest {

    @Mock
    private DeleteAccountPersistencePort deleteAccountPersistencePort;

    @Mock
    private GetAccountPersistencePort getAccountPersistencePort;

    @InjectMocks
    private DeleteAccountServiceImpl deleteAccountService;

    @Test
    void deleteAccount_existsAndNotReferenced_deletesSuccessfully() {
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(existingAccount()));
        when(deleteAccountPersistencePort.isReferencedByTransaction("id-1")).thenReturn(false);

        assertThatNoException().isThrownBy(() -> deleteAccountService.deleteAccount("id-1"));

        verify(deleteAccountPersistencePort).deleteAccount("id-1");
    }

    @Test
    void deleteAccount_notFound_throwsNoSuchElementException() {
        when(getAccountPersistencePort.getAccountById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteAccountService.deleteAccount("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteAccount_referencedByTransaction_throwsIllegalStateException() {
        when(getAccountPersistencePort.getAccountById("id-1")).thenReturn(Optional.of(existingAccount()));
        when(deleteAccountPersistencePort.isReferencedByTransaction("id-1")).thenReturn(true);

        assertThatThrownBy(() -> deleteAccountService.deleteAccount("id-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account is referenced by a bill or income (including removed or corrected ones, "
                        + "which are retained permanently) and cannot be deleted");
    }

    private AccountDto existingAccount() {
        AccountDto dto = new AccountDto();
        dto.setId("id-1");
        dto.setName("ING Checking");
        dto.setType(AccountType.CHECKING);
        dto.setCurrencies(List.of("EUR"));
        dto.setDefaultCurrency("EUR");
        return dto;
    }
}
