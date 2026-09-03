package at.ymeri.my.finance.controller.auth;

import at.ymeri.my.finance.application.controller.auth.AuthApi;
import at.ymeri.my.finance.application.data.AuthStatus;
import at.ymeri.my.finance.application.data.ChangePasswordRequest;
import at.ymeri.my.finance.application.data.LoginRequest;
import at.ymeri.my.finance.application.data.Session;
import at.ymeri.my.finance.application.data.SetupRequest;
import at.ymeri.my.finance.domain.api.AuthenticateService;
import at.ymeri.my.finance.domain.api.ChangePasswordService;
import at.ymeri.my.finance.domain.api.LogoutService;
import at.ymeri.my.finance.domain.api.SetupAdminAccountService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.security.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every request/response DTO here is either all-primitive ({@code SetupRequest}/{@code LoginRequest}/
 * {@code ChangePasswordRequest} pass straight through as method arguments) or assembled from two
 * unrelated sources ({@code Session} from a {@link JwtTokenService.IssuedToken} plus a username) —
 * there is no same-shaped Domain-DTO-to-API-model transformation for a MapStruct mapper to earn
 * its keep on, unlike every other feature's controller.
 */
@RestController
public class AuthController implements AuthApi {

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final SetupAdminAccountService setupAdminAccountService;
    private final AuthenticateService authenticateService;
    private final LogoutService logoutService;
    private final ChangePasswordService changePasswordService;
    private final JwtTokenService jwtTokenService;

    public AuthController(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                           SetupAdminAccountService setupAdminAccountService,
                           AuthenticateService authenticateService,
                           LogoutService logoutService,
                           ChangePasswordService changePasswordService,
                           JwtTokenService jwtTokenService) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.setupAdminAccountService = setupAdminAccountService;
        this.authenticateService = authenticateService;
        this.logoutService = logoutService;
        this.changePasswordService = changePasswordService;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public ResponseEntity<AuthStatus> getAuthStatus() {
        boolean configured = getAdminAccountPersistencePort.get().isPresent();
        return ResponseEntity.ok(new AuthStatus().adminAccountConfigured(configured));
    }

    @Override
    public ResponseEntity<Session> setupAdminAccount(SetupRequest setupRequest) {
        AdminAccountDto account = setupAdminAccountService.setup(setupRequest.getUsername(), setupRequest.getPassword());
        return ResponseEntity.ok(toSession(account.getUsername(), account.getTokenVersion()));
    }

    @Override
    public ResponseEntity<Session> login(LoginRequest loginRequest) {
        return authenticateService.authenticate(loginRequest.getUsername(), loginRequest.getPassword())
                .map(account -> ResponseEntity.ok(toSession(account.getUsername(), account.getTokenVersion())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public ResponseEntity<Void> logout() {
        logoutService.logout();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest changePasswordRequest) {
        changePasswordService.changePassword(changePasswordRequest.getCurrentPassword(), changePasswordRequest.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private Session toSession(String username, int tokenVersion) {
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(username, tokenVersion);
        return new Session().token(issued.token()).username(username).expiresAt(issued.expiresAt());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleAlreadySetUp(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleWrongCurrentPassword(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }
}
