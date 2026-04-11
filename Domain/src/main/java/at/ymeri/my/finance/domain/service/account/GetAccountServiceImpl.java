package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.GetAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetAccountServiceImpl implements GetAccountService {

    private final GetAccountPersistencePort getAccountPersistencePort;

    public GetAccountServiceImpl(GetAccountPersistencePort getAccountPersistencePort) {
        this.getAccountPersistencePort = getAccountPersistencePort;
    }

    @Override
    public AccountDto getAccountById(String id) {
        return getAccountPersistencePort.getAccountById(id).orElseThrow();
    }

    @Override
    public List<AccountDto> getAll() {
        return getAccountPersistencePort.getAll();
    }
}
