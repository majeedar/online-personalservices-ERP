package edu.university.ops.employee;

import edu.university.ops.shared.workflow.ApprovalType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the employee module for other modules (absence, travel, time).
 * Returns read-only views, never entities (ADR-003).
 */
public interface EmployeeDirectory {

    Optional<EmployeeSummary> findEmployee(UUID employeeId);

    /** The work schedule valid on {@code date}, if any. */
    Optional<WorkScheduleView> workScheduleOn(UUID employeeId, LocalDate date);

    /** All work schedules of the employee, newest first (for calculations across periods). */
    List<WorkScheduleView> workSchedules(UUID employeeId);

    /** True if an active or future employment covers the whole period (AGENT.md §13.7). */
    boolean hasEmploymentCovering(UUID employeeId, LocalDate from, LocalDate to);

    /** Approvers of {@code employeeId} for {@code type} on {@code date}, best priority first. */
    List<UUID> approversOf(UUID employeeId, ApprovalType type, LocalDate date);

    boolean isApproverOf(UUID approverId, UUID employeeId, ApprovalType type, LocalDate date);

    /** Active employees whose {@code type} approvals the approver holds on {@code date}. */
    List<EmployeeSummary> employeesApprovedBy(UUID approverId, ApprovalType type, LocalDate date);

    /** Active employees of the given organisational units (for reports). */
    List<EmployeeSummary> employeesOfUnits(List<UUID> organisationUnitIds);

    /** All active employees (batch jobs). */
    List<EmployeeSummary> activeEmployees();

    record EmployeeSummary(UUID id, String personnelNumber, String displayName, String email,
                           UUID organisationUnitId, boolean active) {
    }

    record WorkScheduleView(UUID id, LocalDate validFrom, LocalDate validTo, int weeklyTargetMinutes,
                            Map<DayOfWeek, DayTarget> days) {

        public boolean isValidOn(LocalDate date) {
            return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
        }

        public int targetMinutesOn(DayOfWeek day) {
            DayTarget target = days.get(day);
            return target == null ? 0 : target.targetMinutes();
        }

        public boolean isWorkingDay(DayOfWeek day) {
            DayTarget target = days.get(day);
            return target != null && target.workingDay();
        }
    }

    record DayTarget(int targetMinutes, boolean workingDay) {
    }
}
