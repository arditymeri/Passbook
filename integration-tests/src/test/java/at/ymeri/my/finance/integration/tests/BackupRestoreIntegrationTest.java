package at.ymeri.my.finance.integration.tests;

import at.ymeri.my.finance.MyFinanceApplication;
import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CategoryType;
import at.ymeri.my.finance.application.data.CreateCategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the backup and restore procedure in {@code docs/BACKUP.md} actually works, rather than
 * only being written down (feature 021, FR-012 — "MUST have been executed end-to-end
 * successfully, not merely described").
 *
 * <p><strong>The {@code pg_dump} and {@code pg_restore} invocations here must stay identical to
 * the ones in that document.</strong> A test that dumps and restores by some other route verifies
 * something no operator will ever run, and the requirement would look met while being unmet in
 * substance. If you change one, change both.
 *
 * <p><strong>Why it restores into a second database.</strong> This mirrors the spec's actual
 * scenario — "given a backup artifact and an empty database" — and keeps the test from destroying
 * the container that the other ~110 integration tests share a Spring context with. The commands
 * are the documented ones either way: {@code --clean --if-exists} is a no-op against an empty
 * database and drops-then-recreates against a populated one.
 *
 * <p><strong>What this does not cover.</strong> Booting a second application context against the
 * restored database. The assertions read the restored data through {@code psql}, which proves the
 * artifact is complete and restorable; it does not re-prove that the app starts against it.
 */
@SpringBootTest(
        classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EmbeddedKafka(partitions = 1, topics = {"booking.topic", "transaction.topic"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public class BackupRestoreIntegrationTest {

    /** Written inside the container; never leaves it, and dies with the container. */
    private static final String DUMP_PATH = "/tmp/passbook-test.dump";

    private static final String RESTORE_DB = "passbook_restore_check";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void backupThenRestoreIntoEmptyDatabase_bringsTheDataBack() throws Exception {
        // Uniquely named, so this test does not depend on what the rest of the suite left behind.
        String name = "backup-restore-" + UUID.randomUUID();
        ResponseEntity<CategoryResponse> created = restTemplate.postForEntity(
                "/categories", new CreateCategoryRequest(name, CategoryType.EXPENSE), CategoryResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String user = postgres.getUsername();
        String db = postgres.getDatabaseName();

        // --- Back up: the command from docs/BACKUP.md (-f instead of a shell redirect, which is
        //     the same thing without needing a shell). ---
        exec("pg_dump", "-U", user, "-Fc", db, "-f", DUMP_PATH);
        assertThat(Long.parseLong(exec("stat", "-c", "%s", DUMP_PATH).trim()))
                .as("the dump must be a real artifact, not an empty file")
                .isGreaterThan(0L);

        // --- An empty database to restore into. ---
        exec("dropdb", "-U", user, "--if-exists", RESTORE_DB);
        exec("createdb", "-U", user, RESTORE_DB);
        assertThat(countTables(RESTORE_DB))
                .as("the restore target really is empty beforehand")
                .isEqualTo("0");

        // --- Restore: the command from docs/BACKUP.md. ---
        exec("sh", "-c", "pg_restore -U " + user + " -d " + RESTORE_DB
                + " --clean --if-exists < " + DUMP_PATH);

        // --- The financial record written before the backup is present after the restore. ---
        assertThat(query(RESTORE_DB, "SELECT count(*) FROM category WHERE name = '" + name + "';"))
                .as("the category written before the backup is present after the restore")
                .isEqualTo("1");

        // --- The whole schema came back, not just the table we happened to check. ---
        assertThat(Integer.parseInt(countTables(RESTORE_DB)))
                .as("every application table is restored, not a subset")
                .isGreaterThanOrEqualTo(10);

        // --- The schema-version history travels inside the dump. This is what makes a backup from
        //     a newer version refuse to load into older code (docs/BACKUP.md). ---
        assertThat(query(RESTORE_DB, "SELECT count(*) FROM flyway_schema_history;"))
                .as("flyway_schema_history is restored too, so the backup carries its own "
                        + "schema provenance")
                .isNotEqualTo("0");

        exec("dropdb", "-U", user, "--if-exists", RESTORE_DB);
    }

    private String exec(String... command) throws Exception {
        Container.ExecResult result = postgres.execInContainer(command);
        assertThat(result.getExitCode())
                .as("command failed: %s%nstdout: %s%nstderr: %s",
                        String.join(" ", command), result.getStdout(), result.getStderr())
                .isZero();
        return result.getStdout();
    }

    /** Value-only output, so assertions compare the answer rather than psql's table drawing. */
    private String query(String database, String sql) throws Exception {
        return exec("psql", "-U", postgres.getUsername(), "-d", database, "-t", "-A", "-c", sql).trim();
    }

    private String countTables(String database) throws Exception {
        return query(database,
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';");
    }
}
