package at.ymeri.my.finance.domain.service.auth;

import at.ymeri.my.finance.domain.api.ChangePasswordService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.PasswordHasher;
import at.ymeri.my.finance.domain.spi.auth.SaveAdminAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Service
public class ChangePasswordServiceImpl implements ChangePasswordService {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final SaveAdminAccountPersistencePort saveAdminAccountPersistencePort;
    private final PasswordHasher passwordHasher;

    public ChangePasswordServiceImpl(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                                      SaveAdminAccountPersistencePort saveAdminAccountPersistencePort,
                                      PasswordHasher passwordHasher) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.saveAdminAccountPersistencePort = saveAdminAccountPersistencePort;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void changePassword(String currentPassword, String newPassword) {
        AdminAccountDto account = getAdminAccountPersistencePort.get()
                .orElseThrow(() -> new NoSuchElementException("No admin account configured"));
        if (!passwordHasher.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        // The new password must meet the minimum; the CURRENT one is never checked against it,
        // so an operator whose password predates the rule can still change it (FR-010).
        PasswordPolicy.requireAcceptable(newPassword);
        account.setPasswordHash(passwordHasher.hash(newPassword));
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setUpdatedAt(OffsetDateTime.now());
        saveAdminAccountPersistencePort.save(account);
    }
}
