package edu.university.ops.shared.integration;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Integration settings (AGENT.md §48).
 *
 * @param mode          {@code stub}: in-memory adapters (tests, local dev); {@code http}: the mock-erp container
 * @param mockErpUrl    base URL of the mock-erp service in http mode
 * @param retryAttempts attempts per external call before the call counts as failed
 * @param outboxMaxAttempts outbox deliveries before an export is marked FAILED and admins are alerted
 */
@Validated
@ConfigurationProperties(prefix = "ops.integration")
public record IntegrationProperties(@DefaultValue("stub") String mode,
                                    @DefaultValue("http://localhost:8090") String mockErpUrl,
                                    @DefaultValue("2s") Duration connectTimeout,
                                    @DefaultValue("5s") Duration readTimeout,
                                    @DefaultValue("3") @Min(1) int retryAttempts,
                                    @DefaultValue("200ms") Duration retryBackoff,
                                    @DefaultValue("5") @Min(1) int outboxMaxAttempts) {

    public boolean http() {
        return "http".equalsIgnoreCase(mode);
    }
}
