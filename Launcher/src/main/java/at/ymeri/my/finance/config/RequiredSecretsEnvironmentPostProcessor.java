package at.ymeri.my.finance.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refuses to start when a required secret is missing, naming what is missing (feature 021,
 * FR-009).
 *
 * <p>This exists because a {@code ${VAR}} placeholder with no default is <em>not</em> enough on
 * its own. {@code @Value} throws on an unresolvable placeholder, but Spring Boot's
 * {@code @ConfigurationProperties} binder — which is what binds {@code spring.datasource.*} —
 * leaves it as literal text instead. Without this check, a missing {@code POSTGRES_PASSWORD}
 * surfaces as a confusing authentication or connection failure much later, rather than as
 * "you did not set POSTGRES_PASSWORD".
 *
 * <p>It runs as an {@link EnvironmentPostProcessor} rather than a bean so the failure is the
 * first thing the operator sees, before any datasource, migration or web server is touched.
 * {@link Ordered#LOWEST_PRECEDENCE} places it after Spring Boot's own config-data processing, so
 * values supplied by {@code application.properties}/{@code application.yaml} (including test
 * fixtures) are already visible.
 */
public class RequiredSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Map<String, String> REQUIRED = Map.of(
            "POSTGRES_PASSWORD", "the password Passbook uses to connect to PostgreSQL",
            "JWT_SECRET", "the key Passbook signs session tokens with");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((name, purpose) -> {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.add(name + " (" + purpose + ")");
            }
        });
        if (!missing.isEmpty()) {
            missing.sort(String::compareTo);
            throw new IllegalStateException(
                    "Passbook cannot start: required secret(s) not configured — "
                            + String.join(", ", missing)
                            + ". These deliberately have no built-in default: a shipped fallback "
                            + "would mean every install shares a credential. Copy .env.example to "
                            + ".env and fill it in, then start again. See README and "
                            + "docs/UPGRADING.md.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
