package at.ymeri.my.finance.domain.api;

public interface LogoutService {

    /**
     * Bumps the admin account's token version, invalidating every outstanding session — there
     * being only one account, this is equivalent to invalidating the caller's own (FR-008).
     */
    void logout();
}
