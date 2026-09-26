package edu.university.ops.employee.api;

import edu.university.ops.employee.api.EmployeeResponses.EmploymentResponse;
import edu.university.ops.employee.api.EmployeeResponses.MeResponse;
import edu.university.ops.employee.api.EmployeeResponses.RoleAssignmentResponse;
import edu.university.ops.employee.api.EmployeeResponses.WorkScheduleResponse;
import edu.university.ops.employee.application.EmployeeService;
import edu.university.ops.organisation.OrganisationDirectory;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Employee")
class MeController {

    private final EmployeeService employeeService;
    private final OrganisationDirectory organisations;
    private final Clock clock;

    MeController(EmployeeService employeeService, OrganisationDirectory organisations, Clock clock) {
        this.employeeService = employeeService;
        this.organisations = organisations;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "The logged-in employee")
    MeResponse me() {
        OpsPrincipal principal = CurrentUser.require();
        var employee = employeeService.currentEmployee(principal);
        var unit = organisations.findUnit(employee.getOrganisationUnitId()).orElse(null);
        return MeResponse.of(employee, unit, principal.roles());
    }

    @GetMapping("/employments")
    @Operation(summary = "All employment relationships of the logged-in employee, newest first")
    List<EmploymentResponse> employments() {
        LocalDate today = LocalDate.now(clock);
        return employeeService.employmentsOf(CurrentUser.require().employeeId()).stream()
                .map(e -> EmploymentResponse.of(e, today))
                .toList();
    }

    @GetMapping("/work-schedule")
    @Operation(summary = "The work schedule valid today")
    WorkScheduleResponse workSchedule() {
        return WorkScheduleResponse.of(employeeService.currentWorkSchedule(CurrentUser.require().employeeId()));
    }

    @GetMapping("/roles")
    @Operation(summary = "Role assignments of the logged-in employee, including inactive ones")
    List<RoleAssignmentResponse> roles() {
        LocalDate today = LocalDate.now(clock);
        return employeeService.roleAssignmentsOf(CurrentUser.require().employeeId()).stream()
                .map(r -> RoleAssignmentResponse.of(r, today))
                .toList();
    }
}
