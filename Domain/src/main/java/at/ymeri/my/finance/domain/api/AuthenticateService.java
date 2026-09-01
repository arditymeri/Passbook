package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;

import java.util.Optional;

public interface AuthenticateService {

    /**
     * Empty if no admin account exists, if the username doesn't match, or if the password
     * doesn't match — the same result whichever it was, so callers can never distinguish which
     * part was wrong (FR-012).
     */
    Optional<AdminAccountDto> authenticate(String username, String password);
}
