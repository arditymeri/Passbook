package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.api.SetupAdminAccountService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.PasswordHasher;
import at.ymeri.my.finance.domain.spi.auth.SaveAdminAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class SetupAdminAccountServiceImpl implements SetupAdminAccountService {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;
    private final PasswordHasher passwordHasher;

    public SetupAdminAccountServiceImpl(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                                         SaveAdminAccountPersistencePort saveAdminAccountPersistencePort,
                                         PasswordHasher passwordHasher) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.saveAdminAccountPersistencePort = saveAdminAccountPersistencePort;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public AdminAccountDto setup(String username, String password) {
        if (getAdminAccountPersistencePort.get().isPresent()) {
            throw new IllegalStateException("An admin account already exists");
        }
        AdminAccountDto account = new AdminAccountDto();
        account.setUsername(username);
        account.setPasswordHash(passwordHasher.hash(password));
        account.setTokenVersion(0);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        return saveAdminAccountPersistencePort.save(account);
    }
}
