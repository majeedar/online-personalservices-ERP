package edu.university.ops.shared.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One execution of an interface: a sync run or a single export (AGENT.md §26). */
@Entity
@Table(name = "integration_run")
public class IntegrationRun {

    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED }

    public enum Trigger { SCHEDULED, MANUAL, EVENT, RETRY }

    @Id
    private UUID id;

    private String interfaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger")
    private Trigger trigger;

    private Instant startedAt;
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int recordsRead;
    private int recordsWritten;
    private int recordsFailed;
    private String correlationId;

    protected IntegrationRun() {
    }

    IntegrationRun(String interfaceName, Trigger trigger, Instant now, String correlationId) {
        this.id = UUID.randomUUID();
        this.interfaceName = interfaceName;
        this.trigger = trigger;
        this.startedAt = now;
        this.status = Status.RUNNING;
        this.correlationId = correlationId;
    }

    void finish(int read, int written, int failed, Instant now) {
        this.recordsRead = read;
        this.recordsWritten = written;
        this.recordsFailed = failed;
        this.finishedAt = now;
        this.status = failed == 0 ? Status.SUCCESS : written > 0 ? Status.PARTIAL : Status.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getInterfaceName() {
        return interfaceName;
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

    public int getRecordsRead() {
        return recordsRead;
    }

    public int getRecordsWritten() {
        return recordsWritten;
    }

    public int getRecordsFailed() {
        return recordsFailed;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
