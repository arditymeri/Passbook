package at.ymeri.my.finance.controller.account;

import at.ymeri.my.finance.application.controller.account.AccountDeleteApi;
import at.ymeri.my.finance.domain.api.DeleteAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class AccountDeleteController implements AccountDeleteApi {

    private final DeleteAccountService deleteAccountService;

    public AccountDeleteController(DeleteAccountService deleteAccountService) {
        this.deleteAccountService = deleteAccountService;
    }

    @Override
    public ResponseEntity<Void> deleteAccount(String id) {
        deleteAccountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
