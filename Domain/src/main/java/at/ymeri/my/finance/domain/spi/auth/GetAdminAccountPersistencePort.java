package at.ymeri.my.finance.domain.spi.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;

import java.util.Optional;

public interface GetAdminAccountPersistencePort {

    /**
     * The one admin account, if setup has ever completed. At most one row ever exists.
     */
    Optional<AdminAccountDto> get();
}
