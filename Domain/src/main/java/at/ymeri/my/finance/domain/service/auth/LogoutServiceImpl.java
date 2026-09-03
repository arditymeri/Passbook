package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.api.LogoutService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.SaveAdminAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class LogoutServiceImpl implements LogoutService {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;

    public LogoutServiceImpl(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                              SaveAdminAccountPersistencePort saveAdminAccountPersistencePort) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.saveAdminAccountPersistencePort = saveAdminAccountPersistencePort;
    }

    @Override
    public void logout() {
        AdminAccountDto account = getAdminAccountPersistencePort.get()
                .orElseThrow(() -> new NoSuchElementException("No admin account configured"));
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setUpdatedAt(OffsetDateTime.now());
        saveAdminAccountPersistencePort.save(account);
    }
}
