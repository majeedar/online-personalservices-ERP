package edu.university.ops.shared.workflow;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import edu.university.ops.shared.workflow.WorkflowEnums.StepStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One run of a workflow for one business object. Owns step and decision state
 * only; the business object's own status is owned by its module (ADR-004).
 */
@Entity
@Table(name = "workflow_instance")
public class WorkflowInstance {

    @Id
    private UUID id;

    private UUID workflowDefinitionId;
    private String businessObjectType;
    private UUID businessObjectId;
    private UUID requesterId;
    private int currentStep;

    @Enumerated(EnumType.STRING)
    private InstanceStatus status;

    private Instant createdAt;
    private Instant completedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepNumber")
    private List<WorkflowStep> steps = new ArrayList<>();

    protected WorkflowInstance() {
    }

    WorkflowInstance(UUID definitionId, String businessObjectType, UUID businessObjectId, UUID requesterId,
                     Instant now) {
        this.id = UUID.randomUUID();
        this.workflowDefinitionId = definitionId;
        this.businessObjectType = businessObjectType;
        this.businessObjectId = businessObjectId;
        this.requesterId = requesterId;
        this.currentStep = 1;
        this.status = InstanceStatus.RUNNING;
        this.createdAt = now;
    }

    WorkflowStep addStep(StepSpec spec) {
        WorkflowStep step = new WorkflowStep(this, steps.size() + 1, spec);
        steps.add(step);
        return step;
    }

    Optional<WorkflowStep> nextPendingStep() {
        return steps.stream().filter(s -> s.getStatus() == StepStatus.PENDING).findFirst();
    }

    void moveTo(WorkflowStep step) {
        this.currentStep = step.getStepNumber();
    }

    void finish(InstanceStatus outcome, Instant now) {
        requireRunning();
        steps.stream().filter(s -> s.getStatus() == StepStatus.PENDING).forEach(WorkflowStep::skip);
        this.status = outcome;
        this.completedAt = now;
    }

    void cancel(Instant now) {
        requireRunning();
        steps.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING || s.getStatus() == StepStatus.ACTIVE)
                .forEach(s -> s.cancel(now));
        this.status = InstanceStatus.CANCELLED;
        this.completedAt = now;
    }

    void requireRunning() {
        if (status != InstanceStatus.RUNNING) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "This workflow is already " + status.name().toLowerCase() + ".");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkflowDefinitionId() {
        return workflowDefinitionId;
    }

    public String getBusinessObjectType() {
        return businessObjectType;
    }

    public UUID getBusinessObjectId() {
        return businessObjectId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<WorkflowStep> getSteps() {
        return List.copyOf(steps);
    }
}
