package at.ymeri.my.finance.integration.tests;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Feature 020 made every existing endpoint require a valid session — these ~110 pre-existing
 * integration tests across 9 files all call the API through a plain, unauthenticated
 * {@code TestRestTemplate}. Rather than touching every test method, this attaches a bearer token
 * to every outgoing request automatically via a {@link RestTemplateBuilder} bean, which Spring
 * Boot's {@code TestRestTemplate} auto-configuration picks up when exactly one such bean is
 * present in the test context.
 *
 * <p>Authentication happens lazily, on the first intercepted request: it derives this server's
 * base URL from that request's own resolved URI, then either sets up (fresh context/database) or
 * logs into (a context shared and already set up by an earlier test class — Spring's test context
 * caching reuses one Spring context, and therefore one instance of this interceptor, across every
 * test class whose {@code @SpringBootTest} configuration matches) a fixed test admin account, and
 * caches the resulting token for this interceptor's lifetime — i.e. for as long as this Spring
 * context (and its one Postgres container) is reused.
 */
@TestConfiguration
public class TestSecurityConfig {

    private static final String TEST_USERNAME = "integration-test-admin";
    private static final String TEST_PASSWORD = "integration-test-password";

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder().additionalInterceptors(new BearerTokenInterceptor());
    }

    private static class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

        private volatile String token;

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            if (token == null) {
                synchronized (this) {
                    if (token == null) {
                        token = authenticate(request.getURI());
                    }
                }
            }
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return execution.execute(request, body);
        }

        private String authenticate(URI sampleUri) {
            String baseUrl = sampleUri.getScheme() + "://" + sampleUri.getAuthority() + "/api/v1";
            RestTemplate bootstrap = new RestTemplate();
            Map<String, String> credentials = Map.of("username", TEST_USERNAME, "password", TEST_PASSWORD);
            try {
                return extractToken(bootstrap.postForObject(baseUrl + "/auth/setup", credentials, Map.class));
            } catch (HttpClientErrorException.Conflict alreadySetUp) {
                return extractToken(bootstrap.postForObject(baseUrl + "/auth/login", credentials, Map.class));
            }
        }

        @SuppressWarnings("unchecked")
        private String extractToken(Map<?, ?> session) {
            return (String) session.get("token");
        }
    }
}
