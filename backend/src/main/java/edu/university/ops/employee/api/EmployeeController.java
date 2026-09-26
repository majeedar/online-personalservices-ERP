package edu.university.ops.employee.api;

import edu.university.ops.employee.api.EmployeeResponses.EmployeeResponse;
import edu.university.ops.employee.application.EmployeeService;
import edu.university.ops.organisation.OrganisationDirectory;
import edu.university.ops.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employee")
class EmployeeController {

    private final EmployeeService employeeService;
    private final OrganisationDirectory organisations;

    EmployeeController(EmployeeService employeeService, OrganisationDirectory organisations) {
        this.employeeService = employeeService;
        this.organisations = organisations;
    }

    record EmployeeSearchResult(UUID id, String displayName, String organisationUnitName) {
    }

    @GetMapping("/search")
    @Operation(summary = "Staff directory search by name (min. 2 characters, max. 20 results; name and unit only)")
    List<EmployeeSearchResult> search(@RequestParam("q") String query) {
        CurrentUser.require();
        return employeeService.search(query).stream()
                .map(e -> new EmployeeSearchResult(e.getId(), e.displayName(),
                        organisations.findUnit(e.getOrganisationUnitId())
                                .map(OrganisationDirectory.OrganisationUnitSummary::name).orElse(null)))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "An employee visible to the caller (self, HR admin, or an active approver)")
    EmployeeResponse get(@PathVariable UUID id) {
        var employee = employeeService.employeeVisibleTo(id, CurrentUser.require());
        var unit = organisations.findUnit(employee.getOrganisationUnitId()).orElse(null);
        return EmployeeResponse.of(employee, unit);
    }
}
