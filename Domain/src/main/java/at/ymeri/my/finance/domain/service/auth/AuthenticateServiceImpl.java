package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.api.AuthenticateService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.PasswordHasher;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticateServiceImpl implements AuthenticateService {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final PasswordHasher passwordHasher;

    public AuthenticateServiceImpl(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                                    PasswordHasher passwordHasher) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Optional<AdminAccountDto> authenticate(String username, String password) {
        return getAdminAccountPersistencePort.get()
                .filter(account -> account.getUsername().equals(username))
                .filter(account -> passwordHasher.matches(password, account.getPasswordHash()));
    }
}
