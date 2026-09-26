package edu.university.ops.travel.application;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.integration.OutboxEvent;
import edu.university.ops.shared.integration.OutboxService;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import edu.university.ops.travel.TravelReports;
import edu.university.ops.travel.domain.FundingSource;
import edu.university.ops.travel.domain.TravelPorts.FundingSourceRepository;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import edu.university.ops.travel.domain.TravelRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TravelQueryService implements TravelReports {

    private final TravelRequestRepository requests;
    private final FundingSourceRepository fundingSources;
    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final OutboxService outbox;
    private final Clock clock;

    TravelQueryService(TravelRequestRepository requests, FundingSourceRepository fundingSources,
                       EmployeeDirectory employees, WorkflowService workflow, OutboxService outbox, Clock clock) {
        this.requests = requests;
        this.fundingSources = fundingSources;
        this.employees = employees;
        this.workflow = workflow;
        this.outbox = outbox;
        this.clock = clock;
    }

    public List<TravelRequest> mine(OpsPrincipal principal) {
        return requests.findByEmployeeIdOrderByStartDateTimeDesc(principal.employeeId());
    }

    /**
     * Object-level access (AGENT.md §47): the traveller, their travel or financial
     * approvers, the travel office, and anyone involved in the trip's workflows.
     */
    public TravelRequest visible(UUID id, OpsPrincipal principal) {
        TravelRequest r = requests.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Travel request"));
        LocalDate today = LocalDate.now(clock);
        boolean allowed = r.getEmployeeId().equals(principal.employeeId())
                || principal.hasRole(Role.TRAVEL_OFFICE)
                || employees.isApproverOf(principal.employeeId(), r.getEmployeeId(), ApprovalType.TRAVEL, today)
                || employees.isApproverOf(principal.employeeId(), r.getEmployeeId(), ApprovalType.FINANCIAL, today)
                || workflow.history(TravelService.BUSINESS_OBJECT_TYPE, id).stream()
                .anyMatch(h -> workflow.isInvolved(h.instanceId(), principal));
        if (!allowed) {
            throw BusinessException.forbidden();
        }
        return r;
    }

    public List<InstanceHistory> history(TravelRequest r) {
        return workflow.history(TravelService.BUSINESS_OBJECT_TYPE, r.getId());
    }

    public Optional<TaskView> actionableTask(TravelRequest r, OpsPrincipal principal) {
        return r.getWorkflowInstanceId() == null ? Optional.empty()
                : workflow.actionableTask(r.getWorkflowInstanceId(), principal);
    }

    public List<OutboxEvent> exports(TravelRequest r) {
        return outbox.eventsOf(r.getId());
    }

    public List<FundingSource> activeFundingSources() {
        return fundingSources.findByActiveTrueOrderByCostCentre();
    }

    public List<FundingSource> fundingSources(List<UUID> ids) {
        return fundingSources.findByIdIn(ids);
    }

    @Override
    public List<TravelSummary> allTrips() {
        return requests.findAll().stream().map(r -> new TravelSummary(r.getId(), r.getEmployeeId(),
                r.getStatus().name(), r.getDestinationCity(), r.getStartDateTime(), r.getEstimatedCost(),
                r.getSettledAmount(), r.getCurrency(), r.getCostCentre())).toList();
    }
}
