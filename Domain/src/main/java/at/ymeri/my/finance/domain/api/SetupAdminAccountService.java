package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;

public interface SetupAdminAccountService {

    /**
     * Creates the one admin account. Throws {@link IllegalStateException} if one already exists
     * — this is what keeps first-run setup a one-time operation (FR-002).
     */
    AdminAccountDto setup(String username, String password);
}
