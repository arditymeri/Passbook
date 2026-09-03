package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateSessionServiceImplTest {

    @Mock
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;

    @InjectMocks
    private ValidateSessionServiceImpl service;

    private AdminAccountDto account(int tokenVersion) {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setUsername("admin");
        dto.setTokenVersion(tokenVersion);
        return dto;
    }

    @Test
    void isValid_matchingUsernameAndCurrentTokenVersion_isValid() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account(2)));

        assertTrue(service.isValid("admin", 2));
    }

    @Test
    void isValid_staleTokenVersion_isInvalid() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account(3)));

        assertFalse(service.isValid("admin", 2));
    }

    @Test
    void isValid_wrongUsername_isInvalid() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account(0)));

        assertFalse(service.isValid("someone-else", 0));
    }

    @Test
    void isValid_noAccountConfigured_isInvalid() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());

        assertFalse(service.isValid("admin", 0));
    }
}
