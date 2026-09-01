package at.ymeri.my.finance.domain.api;

public interface ValidateSessionService {

    /**
     * True only if an admin account exists, {@code username} matches it, and
     * {@code tokenVersion} equals the account's current token version (research.md R2) — a
     * signature- and expiry-valid JWT whose token version is stale (logged out, or a password
     * change happened since) is not a valid session.
     */
    boolean isValid(String username, int tokenVersion);
}
