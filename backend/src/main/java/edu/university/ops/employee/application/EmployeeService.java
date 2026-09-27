package edu.university.ops.employee.application;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.employee.domain.ApprovalRelation;
import edu.university.ops.employee.domain.Employee;
import edu.university.ops.employee.domain.EmployeeRepositories.ApprovalRelationRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.EmployeeRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.EmploymentRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.UserRoleRepository;
import edu.university.ops.employee.domain.EmployeeRepositories.WorkScheduleRepository;
import edu.university.ops.employee.domain.Employment;
import edu.university.ops.employee.domain.UserRole;
import edu.university.ops.employee.domain.WorkSchedule;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.security.UserAccountLookup;
import edu.university.ops.shared.workflow.ApprovalType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee use cases for the API, the {@link EmployeeDirectory} facade for other
 * modules, and the {@link UserAccountLookup} / {@link PersonDirectory} ports of
 * the shared platform.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeService implements EmployeeDirectory, UserAccountLookup, PersonDirectory {

    private final EmployeeRepository employees;
    private final EmploymentRepository employments;
    private final WorkScheduleRepository schedules;
    private final UserRoleRepository userRoles;
    private final ApprovalRelationRepository approvalRelations;
    private final EmployeeAccessPolicy accessPolicy;
    private final Clock clock;

    EmployeeService(EmployeeRepository employees, EmploymentRepository employments, WorkScheduleRepository schedules,
                    UserRoleRepository userRoles, ApprovalRelationRepository approvalRelations,
                    EmployeeAccessPolicy accessPolicy, Clock clock) {
        this.employees = employees;
        this.employments = employments;
        this.schedules = schedules;
        this.userRoles = userRoles;
        this.approvalRelations = approvalRelations;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    // ------------------------------------------------------------ API use cases

    /** Saves the interface and e-mail language of the logged-in employee. */
    @Transactional
    public void choosePreferredLanguage(OpsPrincipal principal, String language) {
        if (!"en".equals(language) && !"de".equals(language)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose English or German.");
        }
        currentEmployee(principal).choosePreferredLanguage(language);
    }

    public Employee currentEmployee(OpsPrincipal principal) {
        return employees.findById(principal.employeeId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee"));
    }

    /** Object-level authorization is enforced here, not in the controller (AGENT.md §47). */
    public Employee employeeVisibleTo(UUID employeeId, OpsPrincipal principal) {
        if (!accessPolicy.canViewEmployee(principal, employeeId, today())) {
            // Checked before the lookup, so unauthorized callers cannot probe which IDs exist.
            throw BusinessException.forbidden();
        }
        return employees.findById(employeeId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee"));
    }

    public List<Employment> employmentsOf(UUID employeeId) {
        return employments.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    public WorkSchedule currentWorkSchedule(UUID employeeId) {
        return scheduleOn(employeeId, today())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.WORK_SCHEDULE_NOT_FOUND,
                        "A work schedule valid today"));
    }

    public List<UserRole> roleAssignmentsOf(UUID employeeId) {
        return userRoles.findByEmployeeId(employeeId);
    }

    /** Staff directory search: only name and unit are exposed (data minimisation, AGENT.md §82). */
    public List<Employee> search(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() < 2) {
            return List.of();
        }
        return employees.searchActiveByName(trimmed);
    }

    // ------------------------------------------------------ UserAccountLookup

    @Override
    public Optional<UserAccount> findActiveAccount(String username) {
        LocalDate today = today();
        return employees.findByUsername(username)
                .filter(Employee::isActive)
                .map(e -> new UserAccount(e.getId(), e.getUsername(), e.displayName(), validRoles(e.getId(), today)));
    }

    private Set<Role> validRoles(UUID employeeId, LocalDate date) {
        Set<Role> roles = userRoles.findByEmployeeId(employeeId).stream()
                .filter(r -> r.isValidOn(date))
                .map(UserRole::role)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return Set.copyOf(roles);
    }

    // -------------------------------------------------------- PersonDirectory

    @Override
    public Optional<Person> findPerson(UUID employeeId) {
        return employees.findById(employeeId)
                .map(e -> new Person(e.getId(), e.displayName(), e.getEmail(), e.isActive(),
                        e.getPreferredLanguage()));
    }

    @Override
    public Map<UUID, String> displayNames(Collection<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return employees.findByIdIn(new LinkedHashSet<>(employeeIds)).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::displayName));
    }

    @Override
    public List<UUID> activeEmployeesWithRole(Role role) {
        LocalDate today = today();
        return userRoles.findByRoleName(role).stream()
                .filter(r -> r.isValidOn(today))
                .map(UserRole::getEmployeeId)
                .distinct()
                .filter(id -> employees.findById(id).map(Employee::isActive).orElse(false))
                .toList();
    }

    // ------------------------------------------------------ EmployeeDirectory

    @Override
    public Optional<EmployeeSummary> findEmployee(UUID employeeId) {
        return employees.findById(employeeId).map(EmployeeService::toSummary);
    }

    @Override
    public Optional<WorkScheduleView> workScheduleOn(UUID employeeId, LocalDate date) {
        return scheduleOn(employeeId, date).map(EmployeeService::toView);
    }

    @Override
    public List<WorkScheduleView> workSchedules(UUID employeeId) {
        return schedules.findByEmployeeIdOrderByValidFromDesc(employeeId).stream().map(EmployeeService::toView)
                .toList();
    }

    @Override
    public boolean hasEmploymentCovering(UUID employeeId, LocalDate from, LocalDate to) {
        return employments.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .filter(e -> e.getStatus() == Employment.Status.ACTIVE || e.getStatus() == Employment.Status.FUTURE)
                .anyMatch(e -> e.covers(from, to));
    }

    @Override
    public List<UUID> approversOf(UUID employeeId, ApprovalType type, LocalDate date) {
        return approvalRelations.findByEmployeeIdAndApprovalTypeOrderByPriority(employeeId, type).stream()
                .filter(r -> r.isValidOn(date))
                .map(ApprovalRelation::getApproverId)
                .toList();
    }

    @Override
    public boolean isApproverOf(UUID approverId, UUID employeeId, ApprovalType type, LocalDate date) {
        return accessPolicy.isApproverOf(approverId, employeeId, type, date);
    }

    @Override
    public List<EmployeeSummary> employeesApprovedBy(UUID approverId, ApprovalType type, LocalDate date) {
        Set<UUID> ids = approvalRelations.findByApproverIdAndApprovalType(approverId, type).stream()
                .filter(r -> r.isValidOn(date))
                .map(ApprovalRelation::getEmployeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        return employees.findByIdIn(ids).stream().filter(Employee::isActive).map(EmployeeService::toSummary)
                .toList();
    }

    @Override
    public List<EmployeeSummary> employeesOfUnits(List<UUID> organisationUnitIds) {
        if (organisationUnitIds.isEmpty()) {
            return List.of();
        }
        return employees.findByActiveTrueAndOrganisationUnitIdIn(organisationUnitIds).stream()
                .map(EmployeeService::toSummary).toList();
    }

    @Override
    public List<EmployeeSummary> activeEmployees() {
        return employees.findAll().stream().filter(Employee::isActive).map(EmployeeService::toSummary).toList();
    }

    // ---------------------------------------------------------------- helpers

    private Optional<WorkSchedule> scheduleOn(UUID employeeId, LocalDate date) {
        return schedules.findByEmployeeIdOrderByValidFromDesc(employeeId).stream()
                .filter(s -> s.isValidOn(date))
                .findFirst();
    }

    private static EmployeeSummary toSummary(Employee e) {
        return new EmployeeSummary(e.getId(), e.getPersonnelNumber(), e.displayName(), e.getEmail(),
                e.getOrganisationUnitId(), e.isActive());
    }

    private static WorkScheduleView toView(WorkSchedule s) {
        Map<DayOfWeek, DayTarget> days = new EnumMap<>(DayOfWeek.class);
        s.getDays().forEach(d -> days.put(d.getWeekday(), new DayTarget(d.getTargetMinutes(), d.isWorkingDay())));
        return new WorkScheduleView(s.getId(), s.getValidFrom(), s.getValidTo(), s.getWeeklyTargetMinutes(),
                Map.copyOf(days));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
