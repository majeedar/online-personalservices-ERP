package edu.university.ops;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.springframework.boot.SpringApplication;

/**
 * Runs the backend without Docker: an embedded PostgreSQL (data is discarded on
 * exit) plus the demo profile. Start with {@code mvn spring-boot:test-run}.
 * For the full stack use {@code docker compose up --build} instead.
 */
public final class LocalDevApplication {

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws IOException {
        EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // best effort on JVM shutdown
            }
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");

        SpringApplication.from(OnlinePersonalservicesApplication::main)
                .withAdditionalProfiles("demo")
                .run(args);
    }
}
