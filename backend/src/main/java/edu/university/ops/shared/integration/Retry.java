package edu.university.ops.shared.integration;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounded retry with linear backoff for external calls (AGENT.md §26). Only
 * {@link ExternalSystemException}s are retried; anything else is a bug or a
 * business rejection and fails immediately.
 */
@Component
public class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private final IntegrationProperties properties;

    Retry(IntegrationProperties properties) {
        this.properties = properties;
    }

    public <T> T call(String operation, Supplier<T> action) {
        int attempts = properties.retryAttempts();
        Duration backoff = properties.retryBackoff();
        ExternalSystemException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (ExternalSystemException e) {
                last = e;
                log.warn("External call failed: operation={} attempt={}/{} code={}", operation, attempt, attempts,
                        e.errorCode());
                if (attempt < attempts) {
                    sleep(backoff.multipliedBy(attempt));
                }
            }
        }
        throw last;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
