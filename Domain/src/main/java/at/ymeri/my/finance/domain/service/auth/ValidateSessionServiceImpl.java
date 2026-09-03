package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.api.ValidateSessionService;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class ValidateSessionServiceImpl implements ValidateSessionService {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;

    public ValidateSessionServiceImpl(GetAdminAccountPersistencePort getAdminAccountPersistencePort) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
    }

    @Override
    public boolean isValid(String username, int tokenVersion) {
        return getAdminAccountPersistencePort.get()
                .filter(account -> account.getUsername().equals(username))
                .filter(account -> account.getTokenVersion() == tokenVersion)
                .isPresent();
    }
}
