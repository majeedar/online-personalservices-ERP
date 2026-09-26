package edu.university.ops.shared.admin;

import edu.university.ops.shared.batch.BatchJobService;
import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.integration.ExternalSystemControl;
import edu.university.ops.shared.integration.IntegrationMonitor;
import edu.university.ops.shared.integration.OutboxEvent;
import edu.university.ops.shared.integration.OutboxService;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.workflow.WorkflowService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/** Operations overview for the admin dashboard (AGENT.md §12, §51, §83). */
@Service
public class SystemHealthService {

    private final HealthEndpoint health;
    private final ExternalSystemControl externalSystems;
    private final OutboxService outbox;
    private final IntegrationMonitor integrations;
    private final BatchJobService batch;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final OpsProperties properties;
    private final Clock clock;

    SystemHealthService(HealthEndpoint health, ExternalSystemControl externalSystems, OutboxService outbox,
                        IntegrationMonitor integrations, BatchJobService batch, WorkflowService workflow,
                        NotificationService notifications, OpsProperties properties, Clock clock) {
        this.health = health;
        this.externalSystems = externalSystems;
        this.outbox = outbox;
        this.integrations = integrations;
        this.batch = batch;
        this.workflow = workflow;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    public record SystemHealth(Map<String, String> components, long pendingExports, long failedExports,
                               long openIntegrationErrors, long failedBatchRunsLast24h, long openWorkflowTasks,
                               long failedNotifications) {
    }

    public SystemHealth overview() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("Database", status("db"));
        components.put("Mail", properties.mail().enabled() ? status("mail") : "DISABLED");
        externalSystems.status().forEach((system, h) -> components.put(label(system.name()), h.name()));
        return new SystemHealth(components, outbox.count(OutboxEvent.Status.PENDING),
                outbox.count(OutboxEvent.Status.FAILED), integrations.openErrorCount(),
                batch.failedSince(Instant.now(clock).minus(Duration.ofDays(1))), workflow.countOpenTasks(),
                notifications.failedDeliveries());
    }

    private String status(String component) {
        HealthComponent c = health.healthForPath(component);
        return c == null ? "UNKNOWN" : c.getStatus().getCode();
    }

    private static String label(String system) {
        return switch (system) {
            case "PERSONNEL_ERP" -> "Personnel ERP";
            case "FINANCE_ERP" -> "Finance ERP";
            case "TRAVEL_ERP" -> "Travel ERP";
            default -> system;
        };
    }

    /** Business gauges for Prometheus/Grafana (AGENT.md §51). */
    @Bean
    static MeterBinder opsGauges(WorkflowService workflow, OutboxService outbox, IntegrationMonitor integrations) {
        return (MeterRegistry registry) -> {
            Gauge.builder("ops.workflow.open_tasks", workflow, WorkflowService::countOpenTasks)
                    .description("Open approval tasks").register(registry);
            Gauge.builder("ops.outbox.pending", outbox, o -> o.count(OutboxEvent.Status.PENDING))
                    .description("External exports waiting for delivery").register(registry);
            Gauge.builder("ops.outbox.failed", outbox, o -> o.count(OutboxEvent.Status.FAILED))
                    .description("External exports that gave up").register(registry);
            Gauge.builder("ops.integration.open_errors", integrations, IntegrationMonitor::openErrorCount)
                    .description("Unresolved integration errors").register(registry);
        };
    }
}
