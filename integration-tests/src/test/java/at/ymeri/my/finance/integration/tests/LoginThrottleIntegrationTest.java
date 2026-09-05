package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login throttling across real HTTP requests — the part the Domain tests cannot reach, since what
 * they prove is that the decision is right, not that it survives being made once per request by a
 * web application.
 *
 * <p><strong>Why this class turns the throttle back on.</strong> It is disabled for every other
 * integration test in {@code application.yaml}, deliberately: the instance-wide tier is shared, and
 * a test class asserting that wrong passwords are rejected would push a shared counter past its
 * threshold and start refusing other classes' perfectly valid logins — surfacing as unrelated
 * features failing authentication with nothing to connect it to. Re-enabling it here gives this
 * class its own Spring context, and so its own throttle, which no other test can reach.
 *
 * <p>The thresholds are lowered so the test reads as a scenario rather than a loop, and the
 * instance-wide one is put far out of reach so that what is being measured is unambiguously the
 * per-caller tier.
 *
 * <p>Requests go through a bare {@link TestRestTemplate} rather than the injected one: the injected
 * template carries {@code TestSecurityConfig}'s bearer-token interceptor, which authenticates
 * lazily on its first use and would therefore try to log in partway through a test whose whole
 * purpose is to make logging in fail.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.login-throttle.enabled=true",
                "app.security.login-throttle.per-caller-threshold=3",
                "app.security.login-throttle.instance-threshold=1000",
                "app.security.login-throttle.window-minutes=15"
        }
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class LoginThrottleIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate anonymous = new TestRestTemplate();

    /**
     * One method rather than several, on purpose. The throttle is shared for the life of this
     * Spring context, so once it is refusing it refuses for the rest of the class — splitting these
     * assertions across test methods would make them depend on execution order, which is exactly
     * the kind of test that passes locally and fails in CI.
     */
    @Test
    void repeatedFailuresAreRefusedAndTheRefusalSaysNothingAboutTheAccount() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(login("integration-test-admin", "definitely-not-the-password").getStatusCode())
                    .as("attempt %d is below the threshold and must be answered normally", attempt)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(login("integration-test-admin", "definitely-not-the-password").getStatusCode())
                .as("past the threshold, attempts stop being answered")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // FR-005: the refusal must say that attempts are being refused and nothing whatsoever about
        // whether the account exists. A 429 for the real username and a 401 for an unknown one
        // would turn the throttle itself into an account-enumeration oracle.
        ResponseEntity<String> unknownUser = login("no-such-user-" + UUID.randomUUID(), "whatever");
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(unknownUser.getBody())
                .as("identical to the refusal for a username that does exist")
                .isEqualTo(login("integration-test-admin", "wrong-again").getBody());

        // And the credentials are genuinely not being examined: the CORRECT password is refused
        // too, which is what proves the 429 is returned before verification rather than after it.
        assertThat(login("integration-test-admin", "integration-test-password").getStatusCode())
                .as("a refusal precedes password verification, so even the right password is refused")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<String> login(String username, String password) {
        return anonymous.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login",
                Map.of("username", username, "password", password),
                String.class);
    }
}
