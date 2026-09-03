package at.ymeri.my.finance.domain.spi.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;

public interface SaveAdminAccountPersistencePort {

    /**
     * Creates the account (null id) or updates it (id set) — the same insert-or-update-by-id
     * convention every other entity's persistence port already uses in this codebase.
     */
    AdminAccountDto save(AdminAccountDto adminAccount);
}
