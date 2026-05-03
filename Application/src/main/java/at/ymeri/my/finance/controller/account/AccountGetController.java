package at.ymeri.my.finance.controller.account;

import at.ymeri.my.finance.application.controller.account.AccountGetApi;
import at.ymeri.my.finance.application.data.AccountListResponse;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.mapper.AccountMapper;
import at.ymeri.my.finance.domain.api.GetAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class AccountGetController implements AccountGetApi {

    private final GetAccountService getAccountService;

    public AccountGetController(GetAccountService getAccountService) {
        this.getAccountService = getAccountService;
    }

    @Override
    public ResponseEntity<AccountResponse> getAccountById(String id) {
        try {
            return ResponseEntity.ok(AccountMapper.INSTANCE.map(getAccountService.getAccountById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<AccountListResponse> listAccounts(String type) {
        List<AccountResponse> items = type != null
                ? AccountMapper.INSTANCE.mapList(getAccountService.getByType(type))
                : AccountMapper.INSTANCE.mapList(getAccountService.getAll());
        return ResponseEntity.ok(new AccountListResponse().accounts(items));
    }
}
