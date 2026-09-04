package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateServiceImplTest {

    @Mock
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private AuthenticateServiceImpl service;

    private AdminAccountDto account() {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setUsername("admin");
        dto.setPasswordHash("hashed-correct");
        return dto;
    }

    @Test
    void authenticate_correctUsernameAndPassword_returnsAccount() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account()));
        lenient().when(passwordHasher.matches("correct", "hashed-correct")).thenReturn(true);

        assertTrue(service.authenticate("admin", "correct").isPresent());
    }

    /**
     * FR-010 and SC-004, feature 024. The minimum length applies where a password is SET, never
     * where one is used — so an account created before that rule existed must go on working. If
     * this ever fails, an upgrade has locked operators out of their own financial history, which
     * is a far worse bug than the weak-password exposure the minimum was added to close.
     *
     * <p>Also why authentication must not check length: rejecting a short password before
     * verifying it would tell whoever submitted it that the stored password is short.
     */
    @Test
    void authenticate_passwordShorterThanTodaysMinimum_stillWorks() {
        AdminAccountDto legacy = account();
        legacy.setPasswordHash("hashed-abc");
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(legacy));
        lenient().when(passwordHasher.matches("abc", "hashed-abc")).thenReturn(true);

        assertTrue(service.authenticate("admin", "abc").isPresent());
    }

    @Test
    void authenticate_wrongPassword_returnsEmpty() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account()));
        lenient().when(passwordHasher.matches("wrong", "hashed-correct")).thenReturn(false);

        assertFalse(service.authenticate("admin", "wrong").isPresent());
    }

    @Test
    void authenticate_wrongUsername_returnsEmpty_sameAsWrongPassword() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account()));

        assertFalse(service.authenticate("someone-else", "correct").isPresent());
    }

    @Test
    void authenticate_noAccountConfigured_returnsEmpty() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());

        assertFalse(service.authenticate("admin", "anything").isPresent());
    }
}
