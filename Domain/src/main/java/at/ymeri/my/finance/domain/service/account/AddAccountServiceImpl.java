package at.ymeri.my.finance.domain.service.account;

import at.ymeri.my.finance.domain.api.AddAccountService;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.spi.account.AddAccountPersistencePort;
import at.ymeri.my.finance.domain.spi.account.GetAccountPersistencePort;
import org.springframework.stereotype.Service;

import java.util.Currency;
import java.util.List;

@Service
public class AddAccountServiceImpl implements AddAccountService {

    private final AddAccountPersistencePort addAccountPersistencePort;
    private final GetAccountPersistencePort getAccountPersistencePort;

    public AddAccountServiceImpl(AddAccountPersistencePort addAccountPersistencePort,
                                 GetAccountPersistencePort getAccountPersistencePort) {
        this.addAccountPersistencePort = addAccountPersistencePort;
        this.getAccountPersistencePort = getAccountPersistencePort;
    }

    @Override
    public AccountDto addAccount(AccountDto accountDto) {
        validate(accountDto);
        if (getAccountPersistencePort.existsByName(accountDto.getName())) {
            throw new IllegalStateException("Account name already exists: " + accountDto.getName());
        }
        return addAccountPersistencePort.addAccount(accountDto);
    }

    static void validate(AccountDto accountDto) {
        if (accountDto.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (accountDto.getName().isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (accountDto.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        List<String> currencies = accountDto.getCurrencies();
        if (currencies == null || currencies.isEmpty()) {
            throw new IllegalArgumentException("At least one currency is required");
        }
        for (String code : currencies) {
            try {
                Currency.getInstance(code);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + code);
            }
        }
        if (!currencies.contains(accountDto.getDefaultCurrency())) {
            throw new IllegalArgumentException("Default currency must be one of the account currencies");
        }
    }
}
