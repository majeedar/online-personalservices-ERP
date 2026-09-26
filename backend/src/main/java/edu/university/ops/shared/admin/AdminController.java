package edu.university.ops.shared.admin;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.ops.shared.audit.AuditLogEntry;
import edu.university.ops.shared.audit.AuditLogRepository;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.batch.BatchJobError;
import edu.university.ops.shared.batch.BatchJobRun;
import edu.university.ops.shared.batch.BatchJobService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.ExternalSystemControl;
import edu.university.ops.shared.integration.IntegrationError;
import edu.university.ops.shared.integration.IntegrationMonitor;
import edu.university.ops.shared.integration.IntegrationRun;
import edu.university.ops.shared.integration.OutboxProcessor;
import edu.university.ops.shared.integration.OutboxService;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration API (AGENT.md §39). URL rule: ERP_ADMIN, SUPPORT, AUDITOR may
 * read; only ERP_ADMIN (and SUPPORT for retries) may change anything. AUDITOR is
 * read-only (AGENT.md §47).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
class AdminController {

    private final BatchJobService batch;
    private final IntegrationMonitor integrations;
    private final OutboxService outbox;
    private final OutboxProcessor outboxProcessor;
    private final ExternalSystemControl externalSystems;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final SystemHealthService health;

    AdminController(BatchJobService batch, IntegrationMonitor integrations, OutboxService outbox,
                    OutboxProcessor outboxProcessor, ExternalSystemControl externalSystems,
                    AuditLogRepository auditLog, AuditService audit, SystemHealthService health) {
        this.batch = batch;
        this.integrations = integrations;
        this.outbox = outbox;
        this.outboxProcessor = outboxProcessor;
        this.externalSystems = externalSystems;
        this.auditLog = auditLog;
        this.audit = audit;
        this.health = health;
    }

    record PageResponse<T>(List<T> items, int page, int size, long total) {
        static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        }
    }

    record BatchJobResponse(String name, String description, String schedule, BatchRunResponse lastRun) {
    }

    record BatchRunResponse(UUID id, String jobName, BatchJobRun.Trigger trigger, Instant startedAt,
                            Instant finishedAt, BatchJobRun.Status status, int processedRecords, int successfulRecords,
                            int failedRecords, String startedBy, String correlationId) {
        static BatchRunResponse of(BatchJobRun r) {
            return new BatchRunResponse(r.getId(), r.getJobName(), r.getTrigger(), r.getStartedAt(),
                    r.getFinishedAt(), r.getStatus(), r.getProcessedRecords(), r.getSuccessfulRecords(),
                    r.getFailedRecords(), r.getStartedBy(), r.getCorrelationId());
        }
    }

    record BatchErrorResponse(String recordReference, String errorCode, String errorMessage) {
        static BatchErrorResponse of(BatchJobError e) {
            return new BatchErrorResponse(e.getRecordReference(), e.getErrorCode(), e.getErrorMessage());
        }
    }

    record IntegrationRunResponse(UUID id, String interfaceName, IntegrationRun.Trigger trigger, Instant startedAt,
                                  Instant finishedAt, IntegrationRun.Status status, int recordsRead,
                                  int recordsWritten, int recordsFailed, String correlationId) {
        static IntegrationRunResponse of(IntegrationRun r) {
            return new IntegrationRunResponse(r.getId(), r.getInterfaceName(), r.getTrigger(), r.getStartedAt(),
                    r.getFinishedAt(), r.getStatus(), r.getRecordsRead(), r.getRecordsWritten(), r.getRecordsFailed(),
                    r.getCorrelationId());
        }
    }

    record IntegrationErrorResponse(UUID id, UUID integrationRunId, String externalReference, String errorCode,
                                    String errorMessage, int retryCount, boolean retryable, Instant createdAt,
                                    boolean resolved, Instant resolvedAt) {
        static IntegrationErrorResponse of(IntegrationError e) {
            return new IntegrationErrorResponse(e.getId(), e.getIntegrationRunId(), e.getExternalReference(),
                    e.getErrorCode(), e.getErrorMessage(), e.getRetryCount(), e.getOutboxEventId() != null,
                    e.getCreatedAt(), e.isResolved(), e.getResolvedAt());
        }
    }

    record AuditResponse(UUID id, Instant timestamp, String actorUsername, UUID actorEmployeeId, String action,
                         String entityType, String entityId, JsonNode oldValue, JsonNode newValue,
                         String correlationId) {
        static AuditResponse of(AuditLogEntry a) {
            return new AuditResponse(a.getId(), a.getTimestamp(), a.getActorUsername(), a.getActorEmployeeId(),
                    a.getAction(), a.getEntityType(), a.getEntityId(), a.getOldValueJson(), a.getNewValueJson(),
                    a.getCorrelationId());
        }
    }

    // ------------------------------------------------------------------ batch

    @GetMapping("/batch-jobs")
    @Operation(summary = "Registered batch jobs with their last run")
    List<BatchJobResponse> jobs() {
        return batch.jobs().stream().map(j -> new BatchJobResponse(j.name(), j.description(), j.defaultCron(),
                batch.lastRun(j.name()).map(BatchRunResponse::of).orElse(null))).toList();
    }

    @GetMapping("/batch-runs")
    PageResponse<BatchRunResponse> batchRuns(@RequestParam(required = false) String job,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(batch.history(job, pageRequest(page, size)).map(BatchRunResponse::of));
    }

    @GetMapping("/batch-runs/{id}/errors")
    List<BatchErrorResponse> batchErrors(@PathVariable UUID id) {
        return batch.errorsOf(id).stream().map(BatchErrorResponse::of).toList();
    }

    @PostMapping("/jobs/{jobName}/run")
    @Operation(summary = "Start a batch job now (ERP_ADMIN)")
    BatchRunResponse runJob(@PathVariable String jobName) {
        OpsPrincipal me = requireRole(Role.ERP_ADMIN);
        audit.record("BATCH_MANUAL_START", "BatchJob", jobName, null, null);
        return BatchRunResponse.of(batch.run(jobName, BatchJobRun.Trigger.MANUAL, me.username()));
    }

    // ------------------------------------------------------------ integration

    @GetMapping("/integration-runs")
    PageResponse<IntegrationRunResponse> integrationRuns(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(integrations.runs(pageRequest(page, size)).map(IntegrationRunResponse::of));
    }

    @GetMapping("/integration-errors")
    PageResponse<IntegrationErrorResponse> integrationErrors(@RequestParam(defaultValue = "true") boolean openOnly,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(integrations.errors(openOnly, pageRequest(page, size))
                .map(IntegrationErrorResponse::of));
    }

    @PostMapping("/integration-errors/{id}/retry")
    @Operation(summary = "Retry the export behind an integration error now")
    IntegrationErrorResponse retry(@PathVariable UUID id) {
        requireRole(Role.ERP_ADMIN, Role.SUPPORT);
        IntegrationError error = integrations.error(id);
        if (error.getOutboxEventId() == null) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "This error belongs to a sync run; start the job again instead.");
        }
        outbox.retry(error.getOutboxEventId());
        outboxProcessor.processDue(IntegrationRun.Trigger.RETRY);
        return IntegrationErrorResponse.of(integrations.error(id));
    }

    @PostMapping("/integration-errors/{id}/resolve")
    @Operation(summary = "Mark an integration error as handled (e.g. after a manual correction)")
    IntegrationErrorResponse resolve(@PathVariable UUID id) {
        requireRole(Role.ERP_ADMIN);
        IntegrationError error = integrations.resolve(id);
        audit.record("INTEGRATION_ERROR_RESOLVED", "IntegrationError", id, null, null);
        return IntegrationErrorResponse.of(error);
    }

    @PostMapping("/external-systems/{system}/outage")
    @Operation(summary = "Demo: simulate an outage of an external system (AGENT.md §63)")
    Map<ExternalSystem, ExternalSystemControl.Health> outage(@PathVariable ExternalSystem system,
                                                             @RequestParam boolean down) {
        requireRole(Role.ERP_ADMIN);
        externalSystems.simulateOutage(system, down);
        audit.record(down ? "SIMULATED_OUTAGE_STARTED" : "SIMULATED_OUTAGE_ENDED", "ExternalSystem", system.name(),
                null, null);
        return externalSystems.status();
    }

    // ------------------------------------------------------------ audit, health

    @GetMapping("/audit")
    @Operation(summary = "Audit log, newest first; filter by entity")
    PageResponse<AuditResponse> audit(@RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) String entityId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        Page<AuditLogEntry> entries = entityType != null && entityId != null
                ? auditLog.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId,
                pageRequest(page, size))
                : auditLog.findAllByOrderByTimestampDesc(pageRequest(page, size));
        return PageResponse.of(entries.map(AuditResponse::of));
    }

    @GetMapping("/system-health")
    SystemHealthService.SystemHealth systemHealth() {
        return health.overview();
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
    }

    private static OpsPrincipal requireRole(Role... roles) {
        OpsPrincipal me = CurrentUser.require();
        if (!me.hasAnyRole(roles)) {
            throw BusinessException.forbidden();
        }
        return me;
    }
}
