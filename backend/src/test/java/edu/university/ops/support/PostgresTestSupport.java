package edu.university.ops.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides a real PostgreSQL for integration tests (ADR-012): Testcontainers
 * when a Docker daemon is available, otherwise an embedded PostgreSQL binary.
 * Either way Flyway migrations run against the real engine, never H2.
 * One database is shared by all test classes in the JVM.
 */
public abstract class PostgresTestSupport {

    private static final Logger log = LoggerFactory.getLogger(PostgresTestSupport.class);

    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        if (dockerAvailable()) {
            var container = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("ops").withUsername("ops").withPassword("ops");
            container.start();
            URL = container.getJdbcUrl();
            USERNAME = container.getUsername();
            PASSWORD = container.getPassword();
            log.info("Integration tests use Testcontainers PostgreSQL");
        } else {
            try {
                EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
                URL = pg.getJdbcUrl("postgres", "postgres");
                USERNAME = "postgres";
                PASSWORD = "postgres";
                log.info("Docker unavailable: integration tests use embedded PostgreSQL");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static boolean dockerAvailable() {
        // -Dops.test.embedded-postgres=true: skip Docker, e.g. when the local daemon is unreliable.
        if (Boolean.getBoolean("ops.test.embedded-postgres")) {
            return false;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }
}
