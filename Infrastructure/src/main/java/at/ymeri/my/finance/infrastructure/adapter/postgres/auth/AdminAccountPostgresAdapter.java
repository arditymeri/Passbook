package at.ymeri.my.finance.infrastructure.adapter.postgres.auth;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.auth.SaveAdminAccountPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.AdminAccountEntity;
import at.ymeri.my.finance.infrastructure.mapper.AdminAccountMapper;
import at.ymeri.my.finance.infrastructure.repository.AdminAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminAccountPostgresAdapter implements GetAdminAccountPersistencePort, SaveAdminAccountPersistencePort {

    private final AdminAccountRepository adminAccountRepository;

    public AdminAccountPostgresAdapter(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public Optional<AdminAccountDto> get() {
        return Optional.ofNullable(adminAccountRepository.findFirstByOrderByCreatedAtAsc())
                .map(AdminAccountMapper.INSTANCE::map);
    }

    @Override
    public AdminAccountDto save(AdminAccountDto adminAccount) {
        AdminAccountEntity entity = AdminAccountMapper.INSTANCE.map(adminAccount);
        AdminAccountEntity saved = adminAccountRepository.save(entity);
        return AdminAccountMapper.INSTANCE.map(saved);
    }
}
