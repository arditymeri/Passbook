package at.ymeri.my.finance.controller.auth;

import at.ymeri.my.finance.application.controller.auth.AuthApi;
import at.ymeri.my.finance.application.data.AuthStatus;
import at.ymeri.my.finance.application.data.ChangePasswordRequest;
import at.ymeri.my.finance.application.data.LoginRequest;
import at.ymeri.my.finance.application.data.Session;
import at.ymeri.my.finance.application.data.SetupRequest;
import at.ymeri.my.finance.domain.api.AuthenticateService;
import at.ymeri.my.finance.domain.api.ChangePasswordService;
import at.ymeri.my.finance.domain.service.auth.LoginThrottle;
import at.ymeri.my.finance.domain.service.auth.WeakPasswordException;
import at.ymeri.my.finance.domain.api.LogoutService;
import at.ymeri.my.finance.domain.api.SetupAdminAccountService;
import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import at.ymeri.my.finance.security.ClientAddressResolver;
import at.ymeri.my.finance.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Every request/response DTO here is either all-primitive ({@code SetupRequest}/{@code LoginRequest}/
 * {@code ChangePasswordRequest} pass straight through as method arguments) or assembled from two
 * unrelated sources ({@code Session} from a {@link JwtTokenService.IssuedToken} plus a username) —
 * there is no same-shaped Domain-DTO-to-API-model transformation for a MapStruct mapper to earn
 * its keep on, unlike every other feature's controller.
 */
@RestController
public class AuthController implements AuthApi {

    // Declared rather than generated: this module wires annotation processing for MapStruct, which
    // overrides default discovery, so Lombok's @Slf4j would silently produce no field here.
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    private final SetupAdminAccountService setupAdminAccountService;
    private final AuthenticateService authenticateService;
    private final LogoutService logoutService;
    private final ChangePasswordService changePasswordService;
    private final JwtTokenService jwtTokenService;
    private final LoginThrottle loginThrottle;
    private final ClientAddressResolver clientAddressResolver;
    private final HttpServletRequest request;

    public AuthController(GetAdminAccountPersistencePort getAdminAccountPersistencePort,
                           SetupAdminAccountService setupAdminAccountService,
                           AuthenticateService authenticateService,
                           LogoutService logoutService,
                           ChangePasswordService changePasswordService,
                           JwtTokenService jwtTokenService,
                           LoginThrottle loginThrottle,
                           ClientAddressResolver clientAddressResolver,
                           HttpServletRequest request) {
        this.getAdminAccountPersistencePort = getAdminAccountPersistencePort;
        this.setupAdminAccountService = setupAdminAccountService;
        this.authenticateService = authenticateService;
        this.logoutService = logoutService;
        this.changePasswordService = changePasswordService;
        this.jwtTokenService = jwtTokenService;
        this.loginThrottle = loginThrottle;
        this.clientAddressResolver = clientAddressResolver;
        this.request = request;
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

    /**
     * <strong>The order of the two blocks below is the security property, not a style choice.</strong>
     * The refusal is decided and returned before {@code authenticate} is called, so a refused
     * attempt never reaches password verification. Moving the throttle check after it — or letting
     * a refactor merge them into one expression — would leave the response time as an oracle: a 429
     * that still ran the password hash takes measurably longer than one that did not, and an
     * attacker measuring that gets back exactly what the refusal was protecting.
     *
     * <p>The 429 carries no body and is identical whatever username was submitted. It says that
     * attempts are being refused; it must never say anything about whether the account exists.
     */
    @Override
    public ResponseEntity<Session> login(LoginRequest loginRequest) {
        String caller = clientAddressResolver.resolve(request);
        if (loginThrottle.isRefused(caller, Instant.now())) {
            log.warn("Login refused: too many failed attempts, caller={}, username={}",
                    caller, loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        return authenticateService.authenticate(loginRequest.getUsername(), loginRequest.getPassword())
                .map(account -> {
                    loginThrottle.recordSuccess(caller);
                    return ResponseEntity.ok(toSession(account.getUsername(), account.getTokenVersion()));
                })
                .orElseGet(() -> {
                    loginThrottle.recordFailure(caller, Instant.now());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                });
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

    /**
     * Must stay ahead of the {@link IllegalArgumentException} handler below, which answers 401.
     * A weak new password is not an authorization failure — the operator is perfectly well
     * authorized and simply chose a short password, and answering 401 while their session is
     * valid reads as having been logged out.
     */
    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<String> handleWeakPassword(WeakPasswordException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleWrongCurrentPassword(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }
}
