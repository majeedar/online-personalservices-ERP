package edu.university.ops.shared.integration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports external-system availability under /actuator/health (AGENT.md §83).
 * An outage degrades to UNKNOWN rather than DOWN: the platform itself keeps
 * working and queues exports in the outbox.
 */
@Component("externalSystems")
class ExternalSystemsHealthIndicator implements HealthIndicator {

    private final ExternalSystemControl control;

    ExternalSystemsHealthIndicator(ExternalSystemControl control) {
        this.control = control;
    }

    @Override
    public Health health() {
        var status = control.status();
        Health.Builder builder = status.containsValue(ExternalSystemControl.Health.DOWN) ? Health.unknown()
                : Health.up();
        status.forEach((system, health) -> builder.withDetail(system.name(), health.name()));
        return builder.build();
    }
}
