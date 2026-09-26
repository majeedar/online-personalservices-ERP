package edu.university.ops.shared.workflow;

import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import edu.university.ops.shared.workflow.WorkflowEnums.TaskStatus;
import edu.university.ops.shared.workflow.WorkflowViews.DecisionResult;
import edu.university.ops.shared.workflow.WorkflowViews.DecisionView;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import edu.university.ops.shared.workflow.WorkflowViews.StepHistory;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * A small, generic approval engine (AGENT.md §16): sequential steps assigned to a
 * person or a role, decisions, delegation, and events. It is not a BPMN engine.
 *
 * <p>Business modules start workflows with {@link StepSpec}s and react to
 * {@link WorkflowEvents.Completed}; the engine never calls business modules.
 */
@Service
@Transactional
public class WorkflowService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowInstanceRepository instances;
    private final UserTaskRepository tasks;
    private final DelegationService delegations;
    private final PersonDirectory persons;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final OpsProperties properties;
    private final Clock clock;

    WorkflowService(WorkflowDefinitionRepository definitions, WorkflowInstanceRepository instances,
                    UserTaskRepository tasks, DelegationService delegations, PersonDirectory persons,
                    ApplicationEventPublisher events, AuditService audit, OpsProperties properties, Clock clock) {
        this.definitions = definitions;
        this.instances = instances;
        this.tasks = tasks;
        this.delegations = delegations;
        this.persons = persons;
        this.events = events;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ commands

    public UUID start(String definitionCode, String businessObjectType, UUID businessObjectId, UUID requesterId,
                      List<StepSpec> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A workflow needs at least one step");
        }
        WorkflowDefinition definition = definitions.findFirstByCodeAndActiveTrueOrderByVersionDesc(definitionCode)
                .orElseThrow(() -> new IllegalStateException("No active workflow definition " + definitionCode));
        Instant now = Instant.now(clock);
        WorkflowInstance instance = new WorkflowInstance(definition.getId(), businessObjectType, businessObjectId,
                requesterId, now);
        steps.forEach(instance::addStep);
        instances.save(instance);

        activate(instance, instance.getSteps().getFirst(), definitionCode, now);
        audit.record("WORKFLOW_STARTED", "WorkflowInstance", instance.getId(), null,
                Map.of("definition", definitionCode, "businessObjectType", businessObjectType,
                        "businessObjectId", businessObjectId));
        return instance.getId();
    }

    /**
     * Records a decision on an open task and advances the workflow.
     *
     * @param forwardTo required for {@link Decision#FORWARD}, ignored otherwise
     */
    public DecisionResult decide(UUID taskId, Decision decision, String comment, UUID forwardTo,
                                 OpsPrincipal actor) {
        UserTask task = tasks.findById(taskId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Task"));
        task.requireOpen();
        WorkflowStep step = task.getStep();
        WorkflowInstance instance = step.getInstance();
        instance.requireRunning();
        UUID onBehalfOf = authorize(actor, step, instance);

        if ((decision == Decision.REJECT || decision == Decision.RETURN_FOR_CORRECTION)
                && !StringUtils.hasText(comment)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Please give a reason when rejecting or returning a request.");
        }

        Instant now = Instant.now(clock);
        String definitionCode = definitionCode(instance);
        step.record(new ApprovalDecision(actor.employeeId(), onBehalfOf, decision, trim(comment), now));
        task.complete(now);

        switch (decision) {
            case APPROVE -> {
                step.complete(now);
                Optional<WorkflowStep> next = instance.nextPendingStep();
                if (next.isPresent()) {
                    activate(instance, next.get(), definitionCode, now);
                } else {
                    finish(instance, InstanceStatus.APPROVED, definitionCode, actor, comment, now);
                }
            }
            case REJECT -> {
                step.complete(now);
                finish(instance, InstanceStatus.REJECTED, definitionCode, actor, comment, now);
            }
            case RETURN_FOR_CORRECTION -> {
                step.complete(now);
                finish(instance, InstanceStatus.RETURNED, definitionCode, actor, comment, now);
            }
            case FORWARD -> forward(instance, step, task, forwardTo, definitionCode, now);
        }

        audit.record("WORKFLOW_" + decision.name(), "WorkflowInstance", instance.getId(), null,
                auditValues(step, decision, onBehalfOf, forwardTo));
        return new DecisionResult(instance.getId(), instance.getStatus(), instance.getBusinessObjectType(),
                instance.getBusinessObjectId());
    }

    /** Cancels a running workflow, e.g. when the requester withdraws the request. No event is published. */
    public void cancel(UUID instanceId) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Workflow"));
        Instant now = Instant.now(clock);
        tasks.findByInstance(instanceId, TaskStatus.OPEN).forEach(t -> t.cancel(now));
        instance.cancel(now);
        audit.record("WORKFLOW_CANCELLED", "WorkflowInstance", instanceId, null, null);
    }

    // ------------------------------------------------------------------- queries

    /** Open tasks the principal may act on: assigned, via a role, or via an effective delegation. */
    @Transactional(readOnly = true)
    public List<TaskView> openTasksFor(OpsPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        List<Delegation> delegated = delegations.effectiveFor(principal.employeeId(), today);
        Set<UUID> assignees = new HashSet<>();
        assignees.add(principal.employeeId());
        delegated.forEach(d -> assignees.add(d.getDelegatorId()));
        Set<String> roles = principal.roles().stream().map(Role::name).collect(Collectors.toSet());
        if (roles.isEmpty()) {
            roles = Set.of("-");
        }
        return toViews(tasks.findOpenFor(assignees, roles).stream()
                .map(t -> new VisibleTask(t, visibility(principal, t.getStep(), delegated)))
                .filter(v -> v.access() != Access.NONE)
                .filter(v -> !v.task().getStep().getInstance().getRequesterId().equals(principal.employeeId()))
                .toList());
    }

    @Transactional(readOnly = true)
    public TaskView task(UUID taskId, OpsPrincipal principal) {
        UserTask task = tasks.findById(taskId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Task"));
        List<Delegation> delegated = delegations.effectiveFor(principal.employeeId(), LocalDate.now(clock));
        Access access = visibility(principal, task.getStep(), delegated);
        boolean decided = task.getStep().getDecisions().stream()
                .anyMatch(d -> d.getApproverId().equals(principal.employeeId()));
        if (access == Access.NONE && !decided) {
            throw BusinessException.forbidden();
        }
        return toViews(List.of(new VisibleTask(task, access))).getFirst();
    }

    /** The open task of the workflow if the principal may decide it (drives the UI's approve/reject buttons). */
    @Transactional(readOnly = true)
    public Optional<TaskView> actionableTask(UUID instanceId, OpsPrincipal principal) {
        List<Delegation> delegated = delegations.effectiveFor(principal.employeeId(), LocalDate.now(clock));
        return tasks.findByInstance(instanceId, TaskStatus.OPEN).stream()
                .filter(t -> !t.getStep().getInstance().getRequesterId().equals(principal.employeeId()))
                .map(t -> new VisibleTask(t, visibility(principal, t.getStep(), delegated)))
                .filter(v -> v.access() != Access.NONE)
                .findFirst()
                .map(v -> toViews(List.of(v)).getFirst());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> openTaskOf(UUID instanceId) {
        return tasks.findByInstance(instanceId, TaskStatus.OPEN).stream().map(UserTask::getId).findFirst();
    }

    /** True if the person is (or was) an assignee or decider, or currently a delegate of an assignee. */
    @Transactional(readOnly = true)
    public boolean isInvolved(UUID instanceId, OpsPrincipal principal) {
        WorkflowInstance instance = instances.findById(instanceId).orElse(null);
        if (instance == null) {
            return false;
        }
        List<Delegation> delegated = delegations.effectiveFor(principal.employeeId(), LocalDate.now(clock));
        return instance.getSteps().stream().anyMatch(step ->
                principal.employeeId().equals(step.getAssignedEmployeeId())
                        || visibility(principal, step, delegated) != Access.NONE
                        || step.getDecisions().stream().anyMatch(d -> d.getApproverId().equals(principal.employeeId())));
    }

    @Transactional(readOnly = true)
    public List<InstanceHistory> history(String businessObjectType, UUID businessObjectId) {
        List<WorkflowInstance> runs =
                instances.findByBusinessObjectTypeAndBusinessObjectIdOrderByCreatedAt(businessObjectType,
                        businessObjectId);
        Set<UUID> people = new HashSet<>();
        runs.forEach(i -> i.getSteps().forEach(s -> {
            people.add(s.getAssignedEmployeeId());
            s.getDecisions().forEach(d -> {
                people.add(d.getApproverId());
                people.add(d.getOnBehalfOfId());
            });
        }));
        people.remove(null);
        Map<UUID, String> names = persons.displayNames(people);
        return runs.stream().map(i -> new InstanceHistory(i.getId(), definitionCode(i), i.getStatus(),
                i.getCreatedAt(), i.getCompletedAt(),
                i.getSteps().stream().map(s -> new StepHistory(s.getStepNumber(), s.getStepType(),
                        s.getAssignedEmployeeId(), names.get(s.getAssignedEmployeeId()), s.getAssignedRole(),
                        s.getStatus(), s.getStartedAt(), s.getCompletedAt(),
                        s.getDecisions().stream().map(d -> new DecisionView(d.getApproverId(),
                                names.get(d.getApproverId()), d.getOnBehalfOfId(), names.get(d.getOnBehalfOfId()),
                                d.getDecision(), d.getComment(), d.getDecidedAt())).toList())).toList()))
                .toList();
    }

    /** Retention: removes decision comments of all workflows of a business object; returns the count. */
    public int removeDecisionComments(String businessObjectType, UUID businessObjectId) {
        int removed = 0;
        for (WorkflowInstance i : instances.findByBusinessObjectTypeAndBusinessObjectIdOrderByCreatedAt(
                businessObjectType, businessObjectId)) {
            for (WorkflowStep s : i.getSteps()) {
                for (ApprovalDecision d : s.getDecisions()) {
                    if (d.getComment() != null) {
                        d.removeComment();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /** Open tasks older than the configured reminder threshold (used by the reminder batch job). */
    @Transactional(readOnly = true)
    public List<TaskView> overdueForReminder() {
        Instant before = Instant.now(clock).minus(Duration.ofDays(properties.workflow().reminderAfterDays()));
        return toViews(tasks.findOpenCreatedBefore(before).stream()
                .map(t -> new VisibleTask(t, Access.ASSIGNED)).toList());
    }

    /** All open tasks (reporting: pending approvals). */
    @Transactional(readOnly = true)
    public List<TaskView> allOpenTasks() {
        return toViews(tasks.findOpenCreatedBefore(Instant.now(clock).plusSeconds(1)).stream()
                .map(t -> new VisibleTask(t, Access.ASSIGNED)).toList());
    }

    @Transactional(readOnly = true)
    public long countOpenTasks() {
        return tasks.countOpen();
    }

    // ------------------------------------------------------------------- helpers

    private enum Access { NONE, ASSIGNED, ROLE, DELEGATED }

    private record VisibleTask(UserTask task, Access access) {
    }

    private Access visibility(OpsPrincipal principal, WorkflowStep step, List<Delegation> delegated) {
        UUID assignee = step.getAssignedEmployeeId();
        if (principal.employeeId().equals(assignee)) {
            return Access.ASSIGNED;
        }
        if (assignee != null && delegated.stream().anyMatch(d ->
                d.getDelegatorId().equals(assignee) && d.getApprovalType() == step.getApprovalType())) {
            return Access.DELEGATED;
        }
        if (step.getAssignedRole() != null && principal.roles().stream()
                .anyMatch(r -> r.name().equals(step.getAssignedRole()))) {
            return Access.ROLE;
        }
        return Access.NONE;
    }

    /** @return the delegator's ID if the actor acts as a delegate, otherwise null */
    private UUID authorize(OpsPrincipal actor, WorkflowStep step, WorkflowInstance instance) {
        if (actor.employeeId().equals(instance.getRequesterId())) {
            throw new BusinessException(ErrorCode.NOT_AUTHORIZED, "You cannot decide on your own request.");
        }
        List<Delegation> delegated = delegations.effectiveFor(actor.employeeId(), LocalDate.now(clock));
        return switch (visibility(actor, step, delegated)) {
            case ASSIGNED, ROLE -> null;
            case DELEGATED -> step.getAssignedEmployeeId();
            case NONE -> throw BusinessException.forbidden();
        };
    }

    private void activate(WorkflowInstance instance, WorkflowStep step, String definitionCode, Instant now) {
        step.activate(now);
        instance.moveTo(step);
        createTask(instance, step, step.getTaskTitle(), step.getTaskDescription(), definitionCode, now);
    }

    private void createTask(WorkflowInstance instance, WorkflowStep step, String title, String description,
                            String definitionCode, Instant now) {
        LocalDate due = LocalDate.now(clock).plusDays(properties.workflow().taskDueDays());
        UserTask task = tasks.save(new UserTask(step, title, description, due, now));
        events.publishEvent(new WorkflowEvents.TaskCreated(task.getId(), instance.getId(), definitionCode,
                instance.getBusinessObjectType(), instance.getBusinessObjectId(), step.getAssignedEmployeeId(),
                step.getAssignedRole(), step.getApprovalType(), title));
    }

    private void forward(WorkflowInstance instance, WorkflowStep step, UserTask previous, UUID forwardTo,
                         String definitionCode, Instant now) {
        if (forwardTo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose the person to forward the task to.");
        }
        if (forwardTo.equals(instance.getRequesterId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A task cannot be forwarded to the requester.");
        }
        boolean active = persons.findPerson(forwardTo).map(PersonDirectory.Person::active).orElse(false);
        if (!active) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND, "The recipient is not an active employee.");
        }
        step.reassign(forwardTo);
        createTask(instance, step, previous.getTitle(), previous.getDescription(), definitionCode, now);
    }

    private void finish(WorkflowInstance instance, InstanceStatus outcome, String definitionCode, OpsPrincipal actor,
                        String comment, Instant now) {
        instance.finish(outcome, now);
        events.publishEvent(new WorkflowEvents.Completed(instance.getId(), definitionCode,
                instance.getBusinessObjectType(), instance.getBusinessObjectId(), outcome, actor.employeeId(),
                trim(comment)));
    }

    private String definitionCode(WorkflowInstance instance) {
        return definitions.findById(instance.getWorkflowDefinitionId()).map(WorkflowDefinition::getCode)
                .orElse("UNKNOWN");
    }

    private List<TaskView> toViews(List<VisibleTask> visible) {
        Set<UUID> people = new HashSet<>();
        visible.forEach(v -> {
            people.add(v.task().getStep().getAssignedEmployeeId());
            people.add(v.task().getStep().getInstance().getRequesterId());
        });
        people.remove(null);
        Map<UUID, String> names = persons.displayNames(people);
        List<TaskView> result = new ArrayList<>();
        for (VisibleTask v : visible) {
            UserTask t = v.task();
            WorkflowStep s = t.getStep();
            WorkflowInstance i = s.getInstance();
            result.add(new TaskView(t.getId(), t.getTitle(), t.getDescription(), t.getDueDate(), t.getStatus(),
                    t.getCreatedAt(), definitionCode(i), i.getBusinessObjectType(), i.getBusinessObjectId(),
                    s.getStepType(), s.getApprovalType(), s.getAssignedEmployeeId(),
                    names.get(s.getAssignedEmployeeId()), s.getAssignedRole(), i.getRequesterId(),
                    names.get(i.getRequesterId()),
                    v.access() == Access.DELEGATED ? s.getAssignedEmployeeId() : null));
        }
        return result;
    }

    private static Map<String, Object> auditValues(WorkflowStep step, Decision decision, UUID onBehalfOf,
                                                   UUID forwardTo) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("step", step.getStepNumber());
        values.put("stepType", step.getStepType());
        values.put("decision", decision);
        if (onBehalfOf != null) {
            values.put("onBehalfOf", onBehalfOf);
        }
        if (forwardTo != null) {
            values.put("forwardTo", forwardTo);
        }
        return values;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }
}
