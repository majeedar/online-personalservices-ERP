package edu.university.ops.absence.application;

import edu.university.ops.absence.domain.AbsenceDayCalculator;
import edu.university.ops.absence.domain.AbsenceDayCalculator.CalculatedDay;
import edu.university.ops.absence.domain.AbsenceDayCalculator.DayPlan;
import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveEntitlementRepository;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.DayParts;
import edu.university.ops.absence.domain.LeaveEntitlement;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.calendar.HolidayCalendar;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.employee.EmployeeDirectory.WorkScheduleView;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Day calculation and the validation rules of AGENT.md §13.7. Rules are
 * evaluated completely, so the UI can show every problem at once (preview), and
 * the first problem is thrown on submission.
 */
@Component
public class AbsenceRules {

    private final EmployeeDirectory employees;
    private final HolidayCalendar holidays;
    private final AbsenceRequestRepository requests;
    private final LeaveEntitlementRepository entitlements;

    AbsenceRules(EmployeeDirectory employees, HolidayCalendar holidays, AbsenceRequestRepository requests,
                 LeaveEntitlementRepository entitlements) {
        this.employees = employees;
        this.holidays = holidays;
        this.requests = requests;
        this.entitlements = entitlements;
    }

    public record Issue(ErrorCode code, String message) {
        BusinessException toException() {
            return new BusinessException(code, message);
        }
    }

    public List<CalculatedDay> calculateDays(UUID employeeId, LeaveType type, LocalDate start, LocalDate end,
                                             DayParts parts) {
        AbsenceRequest.requireValidRange(start, end);
        List<WorkScheduleView> schedules = employees.workSchedules(employeeId);
        var holidaySet = holidays.holidaysBetween(start, end).keySet();
        return AbsenceDayCalculator.calculate(start, end, parts, type, date -> schedules.stream()
                .filter(s -> s.isValidOn(date))
                .findFirst()
                .map(s -> new DayPlan(s.targetMinutesOn(date.getDayOfWeek()), s.isWorkingDay(date.getDayOfWeek()))),
                holidaySet);
    }

    /**
     * All rule violations for a request with the given calculated days.
     *
     * @param excludeRequestId the request itself when re-validating an existing one
     */
    public List<Issue> check(UUID employeeId, LeaveType type, LocalDate start, LocalDate end, DayParts parts,
                             UUID representativeId, List<CalculatedDay> days, UUID excludeRequestId) {
        List<Issue> issues = new ArrayList<>();
        if (!type.isActive()) {
            issues.add(new Issue(ErrorCode.LEAVE_TYPE_INACTIVE, "This leave type can no longer be requested."));
        }
        boolean active = employees.findEmployee(employeeId).map(EmployeeDirectory.EmployeeSummary::active)
                .orElse(false);
        if (!active) {
            issues.add(new Issue(ErrorCode.EMPLOYEE_INACTIVE, "The employee is not active."));
        }
        if (!employees.hasEmploymentCovering(employeeId, start, end)) {
            issues.add(new Issue(ErrorCode.EMPLOYMENT_NOT_COVERING,
                    "No employment relationship covers the whole requested period."));
        }
        boolean overlaps = requests.findOverlapping(employeeId, AbsenceStatus.BLOCKING, start, end).stream()
                .anyMatch(r -> !r.getId().equals(excludeRequestId) && conflicts(r, start, end, parts));
        if (overlaps) {
            issues.add(new Issue(ErrorCode.ABSENCE_OVERLAP,
                    "The requested absence overlaps with an existing request."));
        }
        if (representativeId != null) {
            if (representativeId.equals(employeeId)) {
                issues.add(new Issue(ErrorCode.INVALID_REPRESENTATIVE, "You cannot be your own representative."));
            } else if (!employees.findEmployee(representativeId).map(EmployeeDirectory.EmployeeSummary::active)
                    .orElse(false)) {
                issues.add(new Issue(ErrorCode.INVALID_REPRESENTATIVE, "The representative is not an active employee."));
            }
        }
        if (days.stream().noneMatch(CalculatedDay::counts)) {
            issues.add(new Issue(ErrorCode.ABSENCE_NO_WORKING_DAYS,
                    "The requested period contains no working days according to your work schedule."));
        }
        if (type.isDeductsEntitlement()) {
            deductionByYear(days).forEach((year, needed) -> {
                Optional<LeaveEntitlement> ent =
                        entitlements.findByEmployeeIdAndYearAndLeaveTypeId(employeeId, year, type.getId());
                if (ent.isEmpty()) {
                    issues.add(new Issue(ErrorCode.NO_LEAVE_ENTITLEMENT,
                            "There is no " + type.getName().toLowerCase() + " entitlement for " + year + "."));
                } else if (!ent.get().covers(needed)) {
                    issues.add(new Issue(ErrorCode.INSUFFICIENT_LEAVE_BALANCE,
                            "Insufficient leave balance for " + year + ": " + needed.stripTrailingZeros().toPlainString()
                                    + " day(s) requested, " + ent.get().remainingDays().stripTrailingZeros()
                                    .toPlainString() + " remaining."));
                }
            });
        }
        if (type.isAttachmentRequired()) {
            // Documents are attached after the draft is saved; checked on submission by AbsenceService.
            issues.add(new Issue(ErrorCode.ATTACHMENT_REQUIRED, "This leave type requires an attachment."));
        }
        return issues;
    }

    /**
     * Two requests conflict if they share a date, unless on every shared date one covers
     * the morning and the other the afternoon (ADR-018).
     */
    static boolean conflicts(AbsenceRequest existing, LocalDate start, LocalDate end, DayParts parts) {
        LocalDate from = start.isAfter(existing.getStartDate()) ? start : existing.getStartDate();
        LocalDate to = end.isBefore(existing.getEndDate()) ? end : existing.getEndDate();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!parts.on(date, start, end).complements(existing.dayPartOn(date))) {
                return true;
            }
        }
        return false;
    }

    public void enforce(List<Issue> issues) {
        if (!issues.isEmpty()) {
            throw issues.getFirst().toException();
        }
    }

    public static Map<Integer, BigDecimal> deductionByYear(List<CalculatedDay> days) {
        Map<Integer, BigDecimal> result = new TreeMap<>();
        days.forEach(d -> result.merge(d.date().getYear(), d.entitlementDeduction(), BigDecimal::add));
        result.values().removeIf(v -> v.signum() == 0);
        return result;
    }
}
