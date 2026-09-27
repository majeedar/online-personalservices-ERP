package edu.university.ops.shared.batch;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.monitoring.CorrelationId;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs batch jobs with history, metrics and failure alerts (AGENT.md §28):
 * <ul>
 *   <li>observable — every run is a {@code batch_job_run} row plus Micrometer metrics;</li>
 *   <li>safe against duplicate processing — a unique index allows one RUNNING row per job;</li>
 *   <li>restartable — runs left RUNNING by a crash are marked FAILED at startup;</li>
 *   <li>auditable — manual starts and outcomes are audited.</li>
 * </ul>
 */
@Service
public class BatchJobService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobService.class);

    private final Map<String, BatchJob> jobs;
    private final BatchJobRunRepository runs;
    private final BatchJobErrorRepository errors;
    private final AuditService audit;
    private final NotificationService notifications;
    private final PersonDirectory persons;
    private final MeterRegistry meters;
    private final TransactionTemplate tx;
    private final Clock clock;

    BatchJobService(List<BatchJob> jobs, BatchJobRunRepository runs, BatchJobErrorRepository errors,
                    AuditService audit, NotificationService notifications, PersonDirectory persons,
                    MeterRegistry meters, TransactionTemplate tx, Clock clock) {
        this.jobs = jobs.stream().collect(Collectors.toMap(BatchJob::name, Function.identity()));
        this.runs = runs;
        this.errors = errors;
        this.audit = audit;
        this.notifications = notifications;
        this.persons = persons;
        this.meters = meters;
        this.tx = tx;
        this.clock = clock;
    }

    public List<BatchJob> jobs() {
        return jobs.values().stream().sorted(Comparator.comparing(BatchJob::name)).toList();
    }

    /**
     * Runs a job synchronously and returns its history record.
     *
     * @throws BusinessException INVALID_WORKFLOW_STATE if the job is already running
     */
    public BatchJobRun run(String jobName, BatchJobRun.Trigger trigger, String startedBy) {
        BatchJob job = Optional.ofNullable(jobs.get(jobName))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        Text.of("Batch job {job} was not found.", "job", jobName)));
        boolean ownCorrelation = CorrelationId.current() == null;
        if (ownCorrelation) {
            CorrelationId.startNew();
        }
        try {
            Optional<Instant> lastSuccess = runs.findFirstByJobNameAndStatusInOrderByStartedAtDesc(jobName,
                    List.of(BatchJobRun.Status.SUCCESS, BatchJobRun.Status.PARTIAL)).map(BatchJobRun::getStartedAt);
            BatchJobRun run;
            try {
                run = tx.execute(s -> runs.saveAndFlush(new BatchJobRun(jobName, trigger, startedBy,
                        Instant.now(clock), CorrelationId.current())));
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                        Text.of("Job {job} is already running.", "job", jobName));
            }
            UUID runId = run.getId();
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            BatchJob.Context context = new BatchJob.Context() {
                public Optional<Instant> lastSuccessfulRun() {
                    return lastSuccess;
                }

                public void success() {
                    ok.incrementAndGet();
                }

                public void failure(String ref, String code, String message) {
                    failed.incrementAndGet();
                    tx.executeWithoutResult(s -> errors.save(new BatchJobError(runId, ref, code, message)));
                }
            };
            boolean crashed = false;
            Timer.Sample sample = Timer.start(meters);
            try {
                job.run(context);
            } catch (RuntimeException e) {
                crashed = true;
                log.error("Batch job {} failed", jobName, e);
                context.failure(null, "JOB_FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            boolean crashedFinal = crashed;
            BatchJobRun finished = tx.execute(s -> {
                BatchJobRun r = runs.findById(runId).orElseThrow();
                r.finish(ok.get(), failed.get(), crashedFinal, Instant.now(clock));
                audit.record("BATCH_RUN", "BatchJob", jobName, null, Map.of("runId", runId, "trigger", trigger,
                        "status", r.getStatus(), "successful", ok.get(), "failed", failed.get()));
                if (r.getStatus() != BatchJobRun.Status.SUCCESS) {
                    persons.activeEmployeesWithRole(Role.ERP_ADMIN).forEach(admin -> notifications.notify(admin,
                            NotificationType.BATCH_FAILURE, "BatchJobRun", runId,
                            Text.of("Batch job {job}: {status}", "job", jobName, "status",
                                    Text.of(r.getStatus().name())),
                            Text.of("{count} record(s) failed. See Administration › Batch Jobs.", "count",
                                    failed.get())));
                }
                return r;
            });
            sample.stop(meters.timer("ops.batch.duration", "job", jobName));
            meters.counter("ops.batch.runs", "job", jobName, "status", finished.getStatus().name()).increment();
            meters.counter("ops.batch.records", "job", jobName, "result", "failed").increment(failed.get());
            meters.counter("ops.batch.records", "job", jobName, "result", "successful").increment(ok.get());
            return finished;
        } finally {
            if (ownCorrelation) {
                CorrelationId.clear();
            }
        }
    }

    public Page<BatchJobRun> history(String jobName, Pageable pageable) {
        return jobName == null ? runs.findAllByOrderByStartedAtDesc(pageable)
                : runs.findByJobNameOrderByStartedAtDesc(jobName, pageable);
    }

    public Optional<BatchJobRun> lastRun(String jobName) {
        return runs.findFirstByJobNameOrderByStartedAtDesc(jobName);
    }

    public List<BatchJobError> errorsOf(UUID runId) {
        return errors.findByBatchJobRunId(runId);
    }

    public long failedSince(Instant since) {
        return runs.countByStatusAndStartedAtAfter(BatchJobRun.Status.FAILED, since)
                + runs.countByStatusAndStartedAtAfter(BatchJobRun.Status.PARTIAL, since);
    }

    /** Restartability: a RUNNING row at startup belongs to a crashed instance. */
    @EventListener(ApplicationReadyEvent.class)
    void abandonStaleRuns() {
        tx.executeWithoutResult(s -> runs.findByStatus(BatchJobRun.Status.RUNNING).forEach(r -> {
            log.warn("Marking stale batch run {} of {} as FAILED", r.getId(), r.getJobName());
            r.abandon(Instant.now(clock));
        }));
    }
}
