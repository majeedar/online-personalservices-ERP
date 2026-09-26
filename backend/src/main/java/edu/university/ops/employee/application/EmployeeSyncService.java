package edu.university.ops.employee.application;

import edu.university.ops.employee.EmployeeEvents;
import edu.university.ops.employee.application.PersonnelEmployeeMapper.EmployeeImportData;
import edu.university.ops.employee.domain.ApprovalRelation;
import edu.university.ops.employee.domain.Employee;
import edu.university.ops.employee.domain.EmployeeRepositories.ApprovalRelationRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.EmployeeRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.EmploymentRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.RoleDefinitionRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.UserRoleRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.WorkScheduleRepository;
import edu.university.ops.employee.domain.Employment;
import edu.university.ops.employee.domain.UserRole;
import edu.university.ops.employee.domain.WorkSchedule;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies validated personnel records (AGENT.md §27.1, §27.3, §27.4). Upserts by
 * stable business keys (personnel number, contract ID), so importing the same
 * record twice changes nothing (AGENT.md §29). Each record is its own transaction.
 */
@Service
@Transactional
public class EmployeeSyncService {

    /** Approval types derived from the ERP supervisor reference. */
    static final List<ApprovalType> SUPERVISOR_TYPES = List.of(ApprovalType.ABSENCE, ApprovalType.TRAVEL,
            ApprovalType.TIME_CORRECTION);

    public enum Outcome { CREATED, UPDATED, UNCHANGED }

    private final EmployeeRepository employees;
    private final EmploymentRepository employments;
    private final WorkScheduleRepository schedules;
    private final UserRoleRepository userRoles;
    private final RoleDefinitionRepository roles;
    private final ApprovalRelationRepository relations;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EmployeeSyncService(EmployeeRepository employees, EmploymentRepository employments,
                        WorkScheduleRepository schedules, UserRoleRepository userRoles,
                        RoleDefinitionRepository roles, ApprovalRelationRepository relations, AuditService audit,
                        ApplicationEventPublisher events, Clock clock) {
        this.employees = employees;
        this.employments = employments;
        this.schedules = schedules;
        this.userRoles = userRoles;
        this.roles = roles;
        this.relations = relations;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Employee, employment and (for new employees) the initial work schedule. */
    public Outcome applyMasterData(EmployeeImportData d) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        Optional<Employee> existing = employees.findByPersonnelNumber(d.personnelNumber());
        if (existing.isEmpty() && employees.findByUsername(d.username()).isPresent()) {
            throw new IllegalArgumentException("Username " + d.username() + " is already taken");
        }
        Employee employee = existing.orElseGet(() -> Employee.imported(d.externalEmployeeId(), d.personnelNumber(),
                d.firstName(), d.lastName(), d.email(), d.username(), d.organisationUnitId(), now));
        List<String> changedFields = new ArrayList<>();
        if (existing.isPresent()) {
            Map<String, Object> before = describe(employee);
            if (employee.applyMasterData(d.firstName(), d.lastName(), d.email(), d.organisationUnitId(), now)) {
                Map<String, Object> after = describe(employee);
                after.forEach((k, v) -> {
                    if (!v.equals(before.get(k))) {
                        changedFields.add(k);
                    }
                });
            }
        }
        boolean deactivated = false;
        if (!d.active() && employee.isActive()) {
            employee.deactivate();
            deactivated = true;
            changedFields.add("active");
        } else if (d.active() && !employee.isActive()) {
            employee.reactivate();
            changedFields.add("active");
        }
        employees.save(employee);

        if (existing.isEmpty()) {
            roles.findByName(Role.EMPLOYEE).ifPresent(r -> userRoles.save(new UserRole(employee.getId(), r,
                    d.contractStart().isBefore(today) ? d.contractStart() : today)));
        }
        WorkSchedule schedule = existing.isEmpty()
                ? schedules.save(WorkSchedule.of(employee.getId(), d.contractStart(), d.dailyTargets()))
                : currentSchedule(employee, today).orElse(null);
        Employment employment = employments.findByExternalEmploymentId(d.contractId())
                .orElseGet(() -> new Employment(employee.getId(), d.contractId()));
        boolean contractChanged = employment.applyContract(d.contractStart(), d.contractEnd(), d.employmentType(),
                d.weeklyHours(), d.fullTimeEquivalent(), schedule == null ? null : schedule.getId(), today);
        employments.save(employment);
        if (contractChanged && existing.isPresent()) {
            changedFields.add("employment");
        }
        if (employee.getPrimaryEmploymentId() == null) {
            employee.usePrimaryEmployment(employment.getId());
        }

        Outcome outcome = existing.isEmpty() ? Outcome.CREATED
                : changedFields.isEmpty() ? Outcome.UNCHANGED : Outcome.UPDATED;
        if (outcome != Outcome.UNCHANGED) {
            // Only field names are audited, not personnel values (data protection, AGENT.md §82).
            audit.record(outcome == Outcome.CREATED ? "EMPLOYEE_IMPORTED" : "EMPLOYEE_UPDATED", "Employee",
                    employee.getId(), null, Map.of("personnelNumber", d.personnelNumber(), "fields",
                            outcome == Outcome.CREATED ? List.of("all") : changedFields));
            events.publishEvent(new EmployeeEvents.EmployeeMasterDataChanged(employee.getId(),
                    outcome == Outcome.CREATED, deactivated));
        }
        return outcome;
    }

    /** Work schedule refresh (AGENT.md §27.4): a changed pattern starts a new schedule today. */
    public Outcome applyWorkSchedule(EmployeeImportData d) {
        LocalDate today = LocalDate.now(clock);
        Employee employee = employees.findByPersonnelNumber(d.personnelNumber())
                .orElseThrow(() -> new IllegalArgumentException("Employee " + d.personnelNumber() + " not imported yet"));
        Optional<WorkSchedule> current = currentSchedule(employee, today);
        if (current.isPresent() && current.get().samePatternAs(d.dailyTargets())) {
            return Outcome.UNCHANGED;
        }
        LocalDate from = d.contractStart().isAfter(today) ? d.contractStart() : today;
        current.ifPresent(s -> s.endBefore(from));
        WorkSchedule next = schedules.save(WorkSchedule.of(employee.getId(), from, d.dailyTargets()));
        employments.findByExternalEmploymentId(d.contractId()).ifPresent(e -> e.applyContract(e.getStartDate(),
                e.getEndDate(), e.getEmploymentType(), d.weeklyHours(), d.fullTimeEquivalent(), next.getId(), today));
        audit.record("WORK_SCHEDULE_UPDATED", "Employee", employee.getId(),
                current.map(s -> Map.<String, Object>of("weeklyTargetMinutes", s.getWeeklyTargetMinutes()))
                        .orElse(null),
                Map.of("weeklyTargetMinutes", next.getWeeklyTargetMinutes(), "validFrom", from));
        events.publishEvent(new EmployeeEvents.EmployeeMasterDataChanged(employee.getId(), false, false));
        return current.isPresent() ? Outcome.UPDATED : Outcome.CREATED;
    }

    /** Supervisor synchronisation (AGENT.md §27.3): approval relations follow SUPERVISOR_REF. */
    public Outcome applySupervisor(EmployeeImportData d) {
        LocalDate today = LocalDate.now(clock);
        Employee employee = employees.findByPersonnelNumber(d.personnelNumber())
                .orElseThrow(() -> new IllegalArgumentException("Employee " + d.personnelNumber() + " not imported yet"));
        if (d.supervisorPersonnelNumber() == null || !employee.isActive()) {
            return Outcome.UNCHANGED;
        }
        Employee supervisor = employees.findByPersonnelNumber(d.supervisorPersonnelNumber())
                .filter(Employee::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Supervisor " + d.supervisorPersonnelNumber() + " is not an active employee"));
        boolean changed = false;
        for (ApprovalType type : SUPERVISOR_TYPES) {
            List<ApprovalRelation> current = relations.findByEmployeeIdAndApprovalTypeOrderByPriority(
                    employee.getId(), type).stream().filter(r -> r.isValidOn(today)).toList();
            if (current.stream().anyMatch(r -> r.getApproverId().equals(supervisor.getId()))) {
                continue;
            }
            current.forEach(r -> r.endOn(today.minusDays(1)));
            relations.save(new ApprovalRelation(employee.getId(), supervisor.getId(), type, today));
            changed = true;
        }
        if (changed) {
            audit.record("APPROVAL_RELATIONS_SYNCED", "Employee", employee.getId(), null,
                    Map.of("approver", supervisor.getId(), "types", SUPERVISOR_TYPES));
        }
        return changed ? Outcome.UPDATED : Outcome.UNCHANGED;
    }

    private Optional<WorkSchedule> currentSchedule(Employee employee, LocalDate today) {
        return schedules.findByEmployeeIdOrderByValidFromDesc(employee.getId()).stream()
                .filter(s -> s.isValidOn(today)).findFirst();
    }

    private static Map<String, Object> describe(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firstName", e.getFirstName());
        m.put("lastName", e.getLastName());
        m.put("email", e.getEmail());
        m.put("organisationUnit", e.getOrganisationUnitId());
        return m;
    }
}
