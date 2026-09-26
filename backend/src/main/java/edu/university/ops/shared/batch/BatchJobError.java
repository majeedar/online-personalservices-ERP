package edu.university.ops.shared.batch;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A record a batch run could not process (AGENT.md §28). */
@Entity
@Table(name = "batch_job_error")
public class BatchJobError {

    @Id
    private UUID id;

    private UUID batchJobRunId;
    private String recordReference;
    private String errorCode;
    private String errorMessage;
    private int retryCount;

    protected BatchJobError() {
    }

    BatchJobError(UUID runId, String recordReference, String errorCode, String errorMessage) {
        this.id = UUID.randomUUID();
        this.batchJobRunId = runId;
        this.recordReference = recordReference;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchJobRunId() {
        return batchJobRunId;
    }

    public String getRecordReference() {
        return recordReference;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
