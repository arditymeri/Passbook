package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutServiceImplTest {

    @Mock
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    @Mock
    private SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;

    @InjectMocks
    private LogoutServiceImpl service;

    @Test
    void logout_bumpsTokenVersion() {
        AdminAccountDto account = new AdminAccountDto();
        account.setUsername("admin");
        account.setTokenVersion(7);
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.of(account));
        lenient().when(saveAdminAccountPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.logout();

        ArgumentCaptor<AdminAccountDto> captor = ArgumentCaptor.forClass(AdminAccountDto.class);
        verify(saveAdminAccountPersistencePort).save(captor.capture());
        assertEquals(8, captor.getValue().getTokenVersion());
    }

    @Test
    void logout_noAccountConfigured_throwsNoSuchElement() {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.logout());
        verifyNoInteractions(saveAdminAccountPersistencePort);
    }
}
