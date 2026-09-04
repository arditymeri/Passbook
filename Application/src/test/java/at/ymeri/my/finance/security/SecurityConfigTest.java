package at.ymeri.my.finance.security;

import at.ymeri.my.finance.controller.auth.AuthController;
import at.ymeri.my.finance.domain.api.AuthenticateService;
import at.ymeri.my.finance.domain.api.ChangePasswordService;
import at.ymeri.my.finance.domain.api.LogoutService;
import at.ymeri.my.finance.domain.api.SetupAdminAccountService;
import at.ymeri.my.finance.domain.api.ValidateSessionService;
import at.ymeri.my.finance.domain.service.auth.LoginThrottle;
import at.ymeri.my.finance.domain.spi.auth.GetAdminAccountPersistencePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A DB-free slice test (no Docker needed) proving the security wiring itself: an unauthenticated
 * request to an endpoint that isn't {@code permitAll} is rejected, the same request with a valid
 * token reaches the controller, and the public {@code /auth/status} endpoint needs no token at
 * all. Exercised against {@link AuthController}'s own non-public endpoint ({@code /auth/logout})
 * rather than a separate feature's controller, so this test stays self-contained — it proves
 * {@link SecurityConfig}/{@link JwtAuthenticationFilter} apply uniformly to every controller,
 * which {@code /auth/logout} demonstrates just as validly as any other endpoint would.
 */
@WebMvcTest(controllers = AuthController.class,
        properties = "app.security.jwt-secret=slice-test-secret")
// ClientAddressResolver and LoginThrottle joined AuthController's constructor with feature 024.
// Both are imported rather than mocked: they are pure and construct freely, and mocking them would
// let this test keep passing if the controller stopped consulting the throttle at all.
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenService.class,
        ClientAddressResolver.class, SecurityConfigTest.ThrottleForSliceTest.class})
class SecurityConfigTest {

    /**
     * A throttle that never refuses. This test is about the filter chain, not about counting
     * attempts — a live throttle would make the outcome depend on how many requests earlier tests
     * in this class happened to make.
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class ThrottleForSliceTest {
        @org.springframework.context.annotation.Bean
        LoginThrottle loginThrottle() {
            return new LoginThrottle(new LoginThrottle.Settings(
                    false, 5, 20, java.time.Duration.ofMinutes(15)));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private GetAdminAccountPersistencePort getAdminAccountPersistencePort;
    @MockBean
    private SetupAdminAccountService setupAdminAccountService;
    @MockBean
    private AuthenticateService authenticateService;
    @MockBean
    private LogoutService logoutService;
    @MockBean
    private ChangePasswordService changePasswordService;
    @MockBean
    private ValidateSessionService validateSessionService;

    @Test
    void protectedEndpoint_noAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validToken_reachesController() throws Exception {
        String token = jwtTokenService.issue("admin", 0).token();
        when(validateSessionService.isValid("admin", 0)).thenReturn(true);

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void authStatus_noAuthorizationHeader_isPublic() throws Exception {
        when(getAdminAccountPersistencePort.get()).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk());
    }
}
