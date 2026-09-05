package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quickstart scenario 10 — a deployed instance serving no API browser and no API description, with
 * the properties the deploy compose file actually sets.
 *
 * <p><strong>The last two tests are the point of this class.</strong> That the documentation is
 * gone is easy and barely worth asserting. What matters is that turning it off moved no
 * authorization boundary: the public endpoints are still public and the protected ones still
 * protected. That is why {@code SecurityConfig} is not edited by this feature at all — with
 * springdoc disabled its {@code permitAll} entries simply permit routes that no longer exist —
 * and this class is what would catch it if somebody later "tidied them up".
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        }
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class ApiDocsDisabledIntegrationTest {

    @LocalServerPort
    private int port;

    /** Deliberately unauthenticated — every assertion here is about what a stranger can reach. */
    private final TestRestTemplate anonymous = new TestRestTemplate();

    private HttpStatus statusOf(String path) {
        return HttpStatus.valueOf(
                anonymous.getForEntity("http://localhost:" + port + "/api/v1" + path, String.class)
                        .getStatusCode().value());
    }

    @Test
    void theMachineReadableDescriptionIsNotServed() {
        assertThat(statusOf("/v3/api-docs")).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void theApiBrowserIsNotServed() {
        assertThat(statusOf("/swagger-ui/index.html")).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void thePublicEndpointsAreStillPublic() {
        // If disabling documentation had disturbed the filter chain, this is where it would show:
        // an instance whose own frontend can no longer ask whether setup has been done.
        assertThat(statusOf("/auth/status")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theProtectedEndpointsAreStillProtected() {
        // And here is the direction that would actually be dangerous.
        assertThat(statusOf("/bills")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
