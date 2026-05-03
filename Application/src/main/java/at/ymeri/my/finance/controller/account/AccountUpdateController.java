package at.ymeri.my.finance.controller.account;

import at.ymeri.my.finance.application.controller.account.AccountUpdateApi;
import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.UpdateAccountRequest;
import at.ymeri.my.finance.application.mapper.AccountMapper;
import at.ymeri.my.finance.domain.api.UpdateAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class AccountUpdateController implements AccountUpdateApi {

    private final UpdateAccountService updateAccountService;

    public AccountUpdateController(UpdateAccountService updateAccountService) {
        this.updateAccountService = updateAccountService;
    }

    @Override
    public ResponseEntity<AccountResponse> updateAccount(String id, UpdateAccountRequest updateAccountRequest) {
        AccountResponse response = AccountMapper.INSTANCE.map(
                updateAccountService.updateAccount(id, AccountMapper.INSTANCE.map(updateAccountRequest))
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
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
