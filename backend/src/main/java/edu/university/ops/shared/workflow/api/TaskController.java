package edu.university.ops.shared.workflow.api;

import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.DecisionResult;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Generic task inbox (AGENT.md §17, §38). */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks")
class TaskController {

    private final WorkflowService workflow;

    TaskController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    record CompleteTaskRequest(@NotNull Decision decision, @Size(max = 1000) String comment, UUID forwardTo) {
    }

    @GetMapping
    @Operation(summary = "Open tasks the caller may act on (assigned, by role, or by delegation)")
    List<TaskView> list() {
        return workflow.openTasksFor(CurrentUser.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One task visible to the caller")
    TaskView get(@PathVariable UUID id) {
        return workflow.task(id, CurrentUser.require());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Decide on a task: APPROVE, REJECT, RETURN_FOR_CORRECTION or FORWARD")
    DecisionResult complete(@PathVariable UUID id, @Valid @RequestBody CompleteTaskRequest body) {
        return workflow.decide(id, body.decision(), body.comment(), body.forwardTo(), CurrentUser.require());
    }
}
