package edu.university.ops.employee.api;

import edu.university.ops.employee.domain.Employee;
import edu.university.ops.employee.domain.Employment;
import edu.university.ops.employee.domain.UserRole;
import edu.university.ops.employee.domain.WorkSchedule;
import edu.university.ops.organisation.OrganisationDirectory.OrganisationUnitSummary;
import edu.university.ops.shared.security.Role;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Response DTOs of the employee API. JPA entities are never serialized directly (AGENT.md §76). */
final class EmployeeResponses {

    private EmployeeResponses() {
    }

    record OrganisationUnitRef(UUID id, String code, String name, String costCentre) {
        static OrganisationUnitRef of(OrganisationUnitSummary u) {
            return u == null ? null : new OrganisationUnitRef(u.id(), u.code(), u.name(), u.costCentre());
        }
    }

    record MeResponse(UUID id, String personnelNumber, String firstName, String lastName, String displayName,
                      String email, String username, OrganisationUnitRef organisationUnit, Set<Role> roles) {
        static MeResponse of(Employee e, OrganisationUnitSummary unit, Set<Role> roles) {
            return new MeResponse(e.getId(), e.getPersonnelNumber(), e.getFirstName(), e.getLastName(),
                    e.displayName(), e.getEmail(), e.getUsername(), OrganisationUnitRef.of(unit), roles);
        }
    }

    record EmployeeResponse(UUID id, String personnelNumber, String displayName, String email,
                            OrganisationUnitRef organisationUnit, boolean active) {
        static EmployeeResponse of(Employee e, OrganisationUnitSummary unit) {
            return new EmployeeResponse(e.getId(), e.getPersonnelNumber(), e.displayName(), e.getEmail(),
                    OrganisationUnitRef.of(unit), e.isActive());
        }
    }

    record EmploymentResponse(UUID id, LocalDate startDate, LocalDate endDate, Employment.Type employmentType,
                              BigDecimal weeklyHours, BigDecimal fullTimeEquivalent, Employment.Status status,
                              boolean current) {
        static EmploymentResponse of(Employment e, LocalDate today) {
            return new EmploymentResponse(e.getId(), e.getStartDate(), e.getEndDate(), e.getEmploymentType(),
                    e.getWeeklyHours(), e.getFullTimeEquivalent(), e.getStatus(), e.isCurrent(today));
        }
    }

    record WorkScheduleResponse(UUID id, LocalDate validFrom, LocalDate validTo, int weeklyTargetMinutes,
                                List<Day> days) {
        record Day(DayOfWeek weekday, int targetMinutes, boolean workingDay) {
        }

        static WorkScheduleResponse of(WorkSchedule s) {
            List<Day> days = s.getDays().stream()
                    .map(d -> new Day(d.getWeekday(), d.getTargetMinutes(), d.isWorkingDay()))
                    .sorted(Comparator.comparing(Day::weekday))
                    .toList();
            return new WorkScheduleResponse(s.getId(), s.getValidFrom(), s.getValidTo(), s.getWeeklyTargetMinutes(),
                    days);
        }
    }

    record RoleAssignmentResponse(Role role, LocalDate validFrom, LocalDate validTo, boolean active) {
        static RoleAssignmentResponse of(UserRole r, LocalDate today) {
            return new RoleAssignmentResponse(r.role(), r.getValidFrom(), r.getValidTo(), r.isValidOn(today));
        }
    }
}
