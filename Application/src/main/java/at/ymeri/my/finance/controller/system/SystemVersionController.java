package at.ymeri.my.finance.controller.system;

import at.ymeri.my.finance.application.controller.system.SystemVersionApi;
import at.ymeri.my.finance.application.data.SystemVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Reports which version of Passbook this instance is running (feature 021, FR-013), so an
 * operator can tell what they are on without inspecting source or build files.
 *
 * <p><strong>Why there is no Domain service behind this.</strong> Every other controller in this
 * module delegates to a Domain service through a port, as Principle VIII requires. A build
 * constant is not business logic and crosses no external boundary — there is no I/O for a port to
 * mediate, so introducing a service and a port here would be the speculative generality the
 * constitution prohibits rather than compliance with it.
 *
 * <p><strong>Why not Actuator.</strong> {@code /actuator/info} is the conventional answer and was
 * rejected deliberately: the frontend consumes this response, and Principle VII requires public
 * REST contracts consumed by clients to be defined in OpenAPI YAML first. A framework-owned
 * response shape is not covered by that contract (021 research R7).
 *
 * <p>{@code version} comes from {@code app.version}, filled in by Maven resource filtering at
 * build time. {@code buildTime} comes from {@code META-INF/build-info.properties} and is simply
 * absent when running from an unfiltered classpath — the contract marks it optional for exactly
 * that reason, so an IDE run reports its version rather than failing.
 */
@RestController
public class SystemVersionController implements SystemVersionApi {

    private final String version;
    private final BuildProperties buildProperties;

    public SystemVersionController(@Value("${app.version:unknown}") String version,
                                   Optional<BuildProperties> buildProperties) {
        this.version = version;
        this.buildProperties = buildProperties.orElse(null);
    }

    @Override
    public ResponseEntity<SystemVersion> getSystemVersion() {
        SystemVersion body = new SystemVersion(version);
        if (buildProperties != null && buildProperties.getTime() != null) {
            body.setBuildTime(OffsetDateTime.ofInstant(buildProperties.getTime(), ZoneOffset.UTC));
        }
        return ResponseEntity.ok(body);
    }
}
