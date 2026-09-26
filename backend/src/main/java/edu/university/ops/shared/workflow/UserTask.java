package edu.university.ops.shared.workflow;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.workflow.WorkflowEnums.TaskStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inbox entry for an ACTIVE workflow step (AGENT.md §17). Assignment is read from
 * the step (ADR-004). The version column protects against two approvers
 * completing the same task concurrently (AGENT.md §79).
 */
@Entity
@Table(name = "user_task")
public class UserTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "workflow_step_id")
    private WorkflowStep step;

    private String title;
    private String description;
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private Instant createdAt;
    private Instant completedAt;

    @Version
    private Long version;

    protected UserTask() {
    }

    UserTask(WorkflowStep step, String title, String description, LocalDate dueDate, Instant now) {
        this.id = UUID.randomUUID();
        this.step = step;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = TaskStatus.OPEN;
        this.createdAt = now;
    }

    void complete(Instant now) {
        requireOpen();
        this.status = TaskStatus.COMPLETED;
        this.completedAt = now;
    }

    void cancel(Instant now) {
        if (status == TaskStatus.OPEN) {
            this.status = TaskStatus.CANCELLED;
            this.completedAt = now;
        }
    }

    void requireOpen() {
        if (status != TaskStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "This task has already been " + status.name().toLowerCase() + ".");
        }
    }

    public UUID getId() {
        return id;
    }

    public WorkflowStep getStep() {
        return step;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
