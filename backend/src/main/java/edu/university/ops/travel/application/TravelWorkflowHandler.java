package edu.university.ops.travel.application;

import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.DelegationService;
import edu.university.ops.shared.workflow.WorkflowEvents;
import edu.university.ops.travel.TravelEvents;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous reactions to workflow events for trips (ADR-004). */
@Component
class TravelWorkflowHandler {

    private final TravelRequestRepository requests;
    private final TravelService travel;
    private final NotificationService notifications;
    private final DelegationService delegations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TravelWorkflowHandler(TravelRequestRepository requests, TravelService travel, NotificationService notifications,
                          DelegationService delegations, ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.travel = travel;
        this.notifications = notifications;
        this.delegations = delegations;
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    void on(WorkflowEvents.Completed event) {
        if (!event.concerns(TravelService.BUSINESS_OBJECT_TYPE)) {
            return;
        }
        requests.findById(event.businessObjectId()).ifPresent(r -> {
            if (TravelService.EXPENSE_WORKFLOW.equals(event.definitionCode())) {
                travel.onExpenseReviewCompleted(r, event.outcome().name(), event.comment());
            } else {
                travel.onApprovalCompleted(r, event.outcome().name(), event.comment());
            }
        });
    }

    @EventListener
    void on(WorkflowEvents.TaskCreated event) {
        if (!event.businessObjectType().equals(TravelService.BUSINESS_OBJECT_TYPE)) {
            return;
        }
        // A financial-approval task appears once the supervisor has approved.
        if (event.approvalType() == ApprovalType.FINANCIAL) {
            requests.findById(event.businessObjectId()).ifPresent(r -> events.publishEvent(
                    new TravelEvents.TravelApproved(r.getId(), r.getEmployeeId(), TravelService.STEP_SUPERVISOR)));
        }
        if (event.assignedEmployeeId() == null) {
            return; // role-assigned (travel office): visible in every holder's inbox
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.assignedEmployeeId());
        recipients.addAll(delegations.effectiveDelegatesOf(event.assignedEmployeeId(), event.approvalType(),
                LocalDate.now(clock)));
        NotificationType type = TravelService.EXPENSE_WORKFLOW.equals(event.definitionCode())
                ? NotificationType.TRAVEL_EXPENSE_REVIEW_REQUIRED : NotificationType.TRAVEL_APPROVAL_REQUIRED;
        recipients.forEach(r -> notifications.notify(r, type, TravelService.BUSINESS_OBJECT_TYPE,
                event.businessObjectId(), "Approval required", event.title()));
    }
}
