package edu.university.ops.absence.application;

import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.workflow.DelegationService;
import edu.university.ops.shared.workflow.WorkflowEvents;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to workflow events for absence requests. Synchronous listeners: they run
 * inside the deciding transaction, so the request status and the workflow state
 * commit together (ADR-004).
 */
@Component
class AbsenceWorkflowHandler {

    private final AbsenceRequestRepository requests;
    private final AbsenceService absences;
    private final NotificationService notifications;
    private final DelegationService delegations;
    private final Clock clock;

    AbsenceWorkflowHandler(AbsenceRequestRepository requests, AbsenceService absences,
                           NotificationService notifications, DelegationService delegations, Clock clock) {
        this.requests = requests;
        this.absences = absences;
        this.notifications = notifications;
        this.delegations = delegations;
        this.clock = clock;
    }

    @EventListener
    void on(WorkflowEvents.Completed event) {
        if (!event.concerns(AbsenceService.BUSINESS_OBJECT_TYPE)) {
            return;
        }
        requests.findById(event.businessObjectId()).ifPresent(request -> {
            if (AbsenceService.CANCELLATION_WORKFLOW.equals(event.definitionCode())) {
                absences.onCancellationCompleted(request, event.outcome().name(), event.comment());
            } else {
                absences.onApprovalCompleted(request, event.outcome().name(), event.comment());
            }
        });
    }

    /** Tells the approver, and anyone currently deputising for them, that a decision is needed. */
    @EventListener
    void on(WorkflowEvents.TaskCreated event) {
        if (!event.businessObjectType().equals(AbsenceService.BUSINESS_OBJECT_TYPE)
                || event.assignedEmployeeId() == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.assignedEmployeeId());
        recipients.addAll(delegations.effectiveDelegatesOf(event.assignedEmployeeId(), event.approvalType(),
                LocalDate.now(clock)));
        recipients.forEach(r -> notifications.notify(r, NotificationType.ABSENCE_APPROVAL_REQUIRED,
                AbsenceService.BUSINESS_OBJECT_TYPE, event.businessObjectId(), "Approval required", event.title()));
    }
}
