package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.PasswordHasher;
import at.ymeri.my.finance.domain.spi.auth.SaveAdminAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupAdminAccountServiceImplTest {

    /**
     * Long enough to satisfy {@link at.ymeri.my.finance.domain.service.auth.PasswordPolicy}
     * (feature 024). The previous fixture was ACCEPTABLE_PASSWORD, which the rule now rejects — the test
     * failing was the rule working, so the fixture moved rather than the minimum.
     */
    private static final String ACCEPTABLE_PASSWORD = "a-long-enough-password";

    @Mock
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    @Mock
    private SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;
    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private SetupAdminAccountServiceImpl service;

    @Test
    void setup_noExistingAccount_createsAccountWithHashedPasswordAndZeroTokenVersion() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());
        when(passwordHasher.hash(ACCEPTABLE_PASSWORD)).thenReturn("hashed-password");
        lenient().when(saveAdminAccountPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setup("admin", ACCEPTABLE_PASSWORD);

        ArgumentCaptor<AdminAccountDto> captor = ArgumentCaptor.forClass(AdminAccountDto.class);
        verify(saveAdminAccountPersistencePort).save(captor.capture());
        AdminAccountDto saved = captor.getValue();
        assertEquals("admin", saved.getUsername());
        assertEquals("hashed-password", saved.getPasswordHash());
        assertEquals(0, saved.getTokenVersion());
    }

    @Test
    void setup_accountAlreadyExists_throwsAndDoesNotSave() {
        AdminAccountDto existing = new AdminAccountDto();
        existing.setUsername("admin");
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.setup("someone-else", "pw"));
        verifyNoInteractions(saveAdminAccountPersistencePort, passwordHasher);
    }
}
