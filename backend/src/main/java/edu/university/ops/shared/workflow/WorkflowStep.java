package edu.university.ops.shared.workflow;

import edu.university.ops.shared.workflow.WorkflowEnums.StepStatus;
import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.i18n.Translator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/** One approval step. Assigned to a person, to a role, or to both (person first). */
@Entity
@Table(name = "workflow_step")
public class WorkflowStep {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id")
    private WorkflowInstance instance;

    private int stepNumber;
    private String stepType;

    @Enumerated(EnumType.STRING)
    private ApprovalType approvalType;

    private UUID assignedEmployeeId;
    private String assignedRole;
    private String taskTitle;
    private String taskDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> taskTitleText;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> taskDescriptionText;

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private Instant startedAt;
    private Instant completedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "workflow_step_id", nullable = false)
    @OrderBy("decidedAt")
    private List<ApprovalDecision> decisions = new ArrayList<>();

    protected WorkflowStep() {
    }

    WorkflowStep(WorkflowInstance instance, int stepNumber, StepSpec spec) {
        this.id = UUID.randomUUID();
        this.instance = instance;
        this.stepNumber = stepNumber;
        this.stepType = spec.stepType();
        this.approvalType = spec.approvalType();
        this.assignedEmployeeId = spec.assignedEmployeeId();
        this.assignedRole = spec.assignedRole() == null ? null : spec.assignedRole().name();
        this.taskTitle = spec.taskTitle().english();
        this.taskDescription = spec.taskDescription() == null ? null : spec.taskDescription().english();
        this.taskTitleText = Translator.toMap(spec.taskTitle());
        this.taskDescriptionText = Translator.toMapOrNull(spec.taskDescription());
        this.status = StepStatus.PENDING;
    }

    void activate(Instant now) {
        this.status = StepStatus.ACTIVE;
        this.startedAt = now;
    }

    void complete(Instant now) {
        this.status = StepStatus.COMPLETED;
        this.completedAt = now;
    }

    void skip() {
        this.status = StepStatus.SKIPPED;
    }

    void cancel(Instant now) {
        this.status = StepStatus.CANCELLED;
        this.completedAt = now;
    }

    void reassign(UUID employeeId) {
        this.assignedEmployeeId = employeeId;
        this.assignedRole = null;
    }

    void record(ApprovalDecision decision) {
        decisions.add(decision);
    }

    public UUID getId() {
        return id;
    }

    public WorkflowInstance getInstance() {
        return instance;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getStepType() {
        return stepType;
    }

    public ApprovalType getApprovalType() {
        return approvalType;
    }

    public UUID getAssignedEmployeeId() {
        return assignedEmployeeId;
    }

    public String getAssignedRole() {
        return assignedRole;
    }

    public Text getTaskTitle() {
        return Translator.stored(taskTitleText, taskTitle);
    }

    /** May be null. */
    public Text getTaskDescription() {
        return Translator.stored(taskDescriptionText, taskDescription);
    }

    public StepStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<ApprovalDecision> getDecisions() {
        return List.copyOf(decisions);
    }
}
