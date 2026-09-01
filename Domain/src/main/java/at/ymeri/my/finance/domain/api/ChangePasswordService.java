package at.ymeri.my.finance.domain.api;

public interface ChangePasswordService {

    /**
     * Re-verifies {@code currentPassword} before accepting {@code newPassword}. Throws
     * {@link java.util.NoSuchElementException} if no admin account exists, or
     * {@link IllegalArgumentException} if {@code currentPassword} doesn't match — in either
     * failure case nothing is changed. On success, bumps the account's token version so every
     * outstanding session (including the caller's own) is invalidated (research.md R2).
     */
    void changePassword(String currentPassword, String newPassword);
}
