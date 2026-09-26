package edu.university.ops.shared.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** History of one batch execution (AGENT.md §28). */
@Entity
@Table(name = "batch_job_run")
public class BatchJobRun {

    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED }

    public enum Trigger { SCHEDULED, MANUAL }

    @Id
    private UUID id;

    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger")
    private Trigger trigger;

    private Instant startedAt;
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int processedRecords;
    private int successfulRecords;
    private int failedRecords;
    private String startedBy;
    private String correlationId;

    protected BatchJobRun() {
    }

    BatchJobRun(String jobName, Trigger trigger, String startedBy, Instant now, String correlationId) {
        this.id = UUID.randomUUID();
        this.jobName = jobName;
        this.trigger = trigger;
        this.startedBy = startedBy;
        this.startedAt = now;
        this.status = Status.RUNNING;
        this.correlationId = correlationId;
    }

    void finish(int successful, int failed, boolean crashed, Instant now) {
        this.successfulRecords = successful;
        this.failedRecords = failed;
        this.processedRecords = successful + failed;
        this.finishedAt = now;
        this.status = crashed ? Status.FAILED : failed == 0 ? Status.SUCCESS : successful > 0 ? Status.PARTIAL
                : Status.FAILED;
    }

    /** Marks a run left RUNNING by a crashed instance as failed, so the job can run again (restartable). */
    void abandon(Instant now) {
        this.status = Status.FAILED;
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Status getStatus() {
        return status;
    }

    public int getProcessedRecords() {
        return processedRecords;
    }

    public int getSuccessfulRecords() {
        return successfulRecords;
    }

    public int getFailedRecords() {
        return failedRecords;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
