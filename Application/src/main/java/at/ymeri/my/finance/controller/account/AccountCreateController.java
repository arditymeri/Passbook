package at.ymeri.my.finance.controller.account;

import at.ymeri.my.finance.application.controller.account.AccountCreateApi;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.mapper.AccountMapper;
import at.ymeri.my.finance.domain.api.AddAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountCreateController implements AccountCreateApi {

    private final AddAccountService addAccountService;

    public AccountCreateController(AddAccountService addAccountService) {
        this.addAccountService = addAccountService;
    }

    @Override
    public ResponseEntity<AccountResponse> createAccount(CreateAccountRequest createAccountRequest) {
        AccountResponse response = AccountMapper.INSTANCE.map(
                addAccountService.addAccount(AccountMapper.INSTANCE.map(createAccountRequest))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
