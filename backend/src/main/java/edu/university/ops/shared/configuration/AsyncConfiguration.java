package edu.university.ops.shared.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables asynchronous event listeners ({@code @ApplicationModuleListener}) and
 * scheduled jobs (outbox delivery, batch jobs).
 * Their deliveries are tracked in the event publication registry (ADR-006).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration {
}
