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

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceImplTest {

    @Mock
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    @Mock
    private SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;
    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private ChangePasswordServiceImpl service;

    private AdminAccountDto account() {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setUsername("admin");
        dto.setPasswordHash("hashed-old");
        dto.setTokenVersion(5);
        return dto;
    }

    @Test
    void changePassword_correctCurrentPassword_rotatesHashAndBumpsTokenVersion() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account()));
        when(passwordHasher.matches("old", "hashed-old")).thenReturn(true);
        when(passwordHasher.hash("new")).thenReturn("hashed-new");
        lenient().when(saveAdminAccountPersistencePort.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.changePassword("old", "new");

        ArgumentCaptor<AdminAccountDto> captor = ArgumentCaptor.forClass(AdminAccountDto.class);
        verify(saveAdminAccountPersistencePort).save(captor.capture());
        assertEquals("hashed-new", captor.getValue().getPasswordHash());
        assertEquals(6, captor.getValue().getTokenVersion());
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsAndDoesNotSave() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account()));
        when(passwordHasher.matches("wrong", "hashed-old")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.changePassword("wrong", "new"));
        verifyNoInteractions(saveAdminAccountPersistencePort);
    }

    @Test
    void changePassword_noAccountConfigured_throwsNoSuchElement() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.changePassword("old", "new"));
        verifyNoInteractions(saveAdminAccountPersistencePort, passwordHasher);
    }
}
