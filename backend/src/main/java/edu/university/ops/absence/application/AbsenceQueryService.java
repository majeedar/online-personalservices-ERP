package edu.university.ops.absence.application;

import edu.university.ops.absence.AbsenceLookup;
import edu.university.ops.absence.AbsenceReports;
import edu.university.ops.absence.domain.AbsenceDay;
import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveEntitlementRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveTypeRepository;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.LeaveEntitlement;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.employee.EmployeeDirectory.EmployeeSummary;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AbsenceQueryService implements AbsenceLookup, AbsenceReports {

    private final AbsenceRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveEntitlementRepository entitlements;
    private final AbsenceAccessPolicy access;
    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final Clock clock;

    AbsenceQueryService(AbsenceRequestRepository requests, LeaveTypeRepository leaveTypes,
                        LeaveEntitlementRepository entitlements, AbsenceAccessPolicy access,
                        EmployeeDirectory employees, WorkflowService workflow, Clock clock) {
        this.requests = requests;
        this.leaveTypes = leaveTypes;
        this.entitlements = entitlements;
        this.access = access;
        this.employees = employees;
        this.workflow = workflow;
        this.clock = clock;
    }

    public List<LeaveType> activeLeaveTypes() {
        return leaveTypes.findByActiveTrueOrderByName();
    }

    public Map<UUID, LeaveType> leaveTypesById() {
        return leaveTypes.findAll().stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
    }

    public List<AbsenceRequest> myRequests(OpsPrincipal principal) {
        return requests.findByEmployeeIdOrderByStartDateDesc(principal.employeeId());
    }

    public AbsenceRequest visibleRequest(UUID id, OpsPrincipal principal) {
        AbsenceRequest request = requests.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Absence request"));
        if (!access.canView(principal, request)) {
            throw BusinessException.forbidden();
        }
        return request;
    }

    public List<InstanceHistory> history(AbsenceRequest request) {
        return workflow.history(AbsenceService.BUSINESS_OBJECT_TYPE, request.getId());
    }

    public List<LeaveEntitlement> balances(UUID employeeId, int year) {
        return entitlements.findByEmployeeIdAndYear(employeeId, year);
    }

    public record TeamAbsence(AbsenceRequest request, EmployeeSummary employee) {
    }

    /** Absences of the approver's team in a window; the leave type is not exposed (data protection). */
    public List<TeamAbsence> teamAbsences(OpsPrincipal approver, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(370).isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "Choose a range of at most one year.");
        }
        Map<UUID, EmployeeSummary> team = employees.employeesApprovedBy(approver.employeeId(), ApprovalType.ABSENCE,
                LocalDate.now(clock)).stream().collect(Collectors.toMap(EmployeeSummary::id, Function.identity()));
        if (team.isEmpty()) {
            return List.of();
        }
        return requests.findOverlappingForEmployees(team.keySet(), AbsenceStatus.BLOCKING, from, to).stream()
                .map(r -> new TeamAbsence(r, team.get(r.getEmployeeId())))
                .toList();
    }

    @Override
    public List<LeaveUsage> leaveUsage(int year) {
        return entitlements.findByYear(year).stream().map(e -> new LeaveUsage(e.getEmployeeId(), e.totalDays(),
                e.getUsedDays(), e.getReservedDays(), e.remainingDays())).toList();
    }

    // ---------------------------------------------------------- AbsenceLookup

    @Override
    public Map<LocalDate, AbsenceMinutes> approvedAbsences(UUID employeeId, LocalDate from, LocalDate to) {
        Map<UUID, LeaveType> types = leaveTypesById();
        Map<LocalDate, AbsenceMinutes> result = new TreeMap<>();
        for (AbsenceRequest r : requests.findOverlapping(employeeId, AbsenceStatus.EFFECTIVE, from, to)) {
            String code = types.get(r.getLeaveTypeId()).getCode();
            for (AbsenceDay d : r.getDays()) {
                if (!d.getDate().isBefore(from) && !d.getDate().isAfter(to)) {
                    // A morning and an afternoon absence may share a date (ADR-018): add them up.
                    result.merge(d.getDate(), new AbsenceMinutes(d.getPlannedMinutes(), d.getCreditedMinutes(), code),
                            (a, b) -> new AbsenceMinutes(a.plannedMinutes() + b.plannedMinutes(),
                                    a.creditedMinutes() + b.creditedMinutes(),
                                    a.leaveTypeCode().equals(b.leaveTypeCode()) ? a.leaveTypeCode()
                                            : a.leaveTypeCode() + "/" + b.leaveTypeCode()));
                }
            }
        }
        return result;
    }
}
