package edu.university.ops.absence.application;

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
import edu.university.ops.employee.EmployeeEvents;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.batch.BatchJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Leave entitlement calculation (AGENT.md §27.5): at the start of the year, when
 * employment data changes, and on demand. Idempotent — it creates missing
 * entitlements and recomputes used/reserved days from the absence days, which
 * are the authoritative source.
 *
 * <p>Rules (configurable placeholders): 30 days for a five-day week, pro rata for
 * fewer working days; up to 10 unused days carry over into the next year and
 * expire on 31 March.
 */
@Component
class LeaveEntitlementJob implements BatchJob {

    static final BigDecimal FULL_ENTITLEMENT = BigDecimal.valueOf(30);
    static final BigDecimal MAX_CARRY_OVER = BigDecimal.TEN;

    private final EmployeeDirectory employees;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveEntitlementRepository entitlements;
    private final AbsenceRequestRepository requests;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    LeaveEntitlementJob(EmployeeDirectory employees, LeaveTypeRepository leaveTypes,
                        LeaveEntitlementRepository entitlements, AbsenceRequestRepository requests,
                        AuditService audit, TransactionTemplate tx, Clock clock) {
        this.employees = employees;
        this.leaveTypes = leaveTypes;
        this.entitlements = entitlements;
        this.requests = requests;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "leave-entitlement-calculation";
    }

    @Override
    public String description() {
        return "Create yearly leave entitlements and recalculate used/reserved days";
    }

    @Override
    public String defaultCron() {
        return "0 0 3 1 1 *";
    }

    @Override
    public void run(Context context) {
        LocalDate today = LocalDate.now(clock);
        List<Integer> years = today.getMonthValue() == 12 ? List.of(today.getYear(), today.getYear() + 1)
                : List.of(today.getYear());
        for (EmployeeSummary e : employees.activeEmployees()) {
            try {
                for (int year : years) {
                    tx.executeWithoutResult(s -> calculate(e.id(), year));
                }
                context.success();
            } catch (RuntimeException ex) {
                context.failure(e.personnelNumber(), "CALCULATION_FAILED", ex.getMessage());
            }
        }
    }

    /** "When relevant employment data changes" (AGENT.md §27.5). */
    @ApplicationModuleListener
    void on(EmployeeEvents.EmployeeMasterDataChanged event) {
        if (!event.deactivated()) {
            calculate(event.employeeId(), LocalDate.now(clock).getYear());
        }
    }

    void calculate(UUID employeeId, int year) {
        LeaveType annual = leaveTypes.findByCode("ANNUAL_LEAVE").orElseThrow();
        Optional<LeaveEntitlement> existing =
                entitlements.findByEmployeeIdAndYearAndLeaveTypeId(employeeId, year, annual.getId());
        LeaveEntitlement entitlement = existing.orElseGet(() -> {
            BigDecimal carryOver = entitlements.findByEmployeeIdAndYearAndLeaveTypeId(employeeId, year - 1,
                            annual.getId())
                    .map(prev -> prev.remainingDays().max(BigDecimal.ZERO).min(MAX_CARRY_OVER))
                    .orElse(BigDecimal.ZERO);
            return new LeaveEntitlement(employeeId, year, annual.getId(), baseDays(employeeId, year), carryOver,
                    BigDecimal.ZERO, LocalDate.of(year, 3, 31));
        });
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (AbsenceRequest r : requests.findByEmployeeIdAndStatusIn(employeeId,
                EnumSet.of(AbsenceStatus.SUBMITTED, AbsenceStatus.IN_APPROVAL, AbsenceStatus.APPROVED,
                        AbsenceStatus.CANCEL_REQUESTED))) {
            if (!r.getLeaveTypeId().equals(annual.getId())) {
                continue;
            }
            BigDecimal days = r.getDays().stream().filter(d -> d.getDate().getYear() == year)
                    .map(AbsenceDay::getEntitlementDeduction).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (AbsenceStatus.EFFECTIVE.contains(r.getStatus())) {
                used = used.add(days);
            } else {
                reserved = reserved.add(days);
            }
        }
        boolean changed = existing.isEmpty() || used.compareTo(entitlement.getUsedDays()) != 0
                || reserved.compareTo(entitlement.getReservedDays()) != 0;
        entitlement.resetCounters(used, reserved);
        entitlements.save(entitlement);
        if (changed) {
            audit.record(existing.isEmpty() ? "LEAVE_ENTITLEMENT_CREATED" : "LEAVE_ENTITLEMENT_RECALCULATED",
                    "LeaveEntitlement", entitlement.getId(), null, Map.of("year", year, "base",
                            entitlement.getBaseDays(), "used", used, "reserved", reserved));
        }
    }

    /** 30 days × working days per week / 5, rounded to half days. */
    BigDecimal baseDays(UUID employeeId, int year) {
        LocalDate reference = LocalDate.of(year, 1, 1).isBefore(LocalDate.now(clock)) ? LocalDate.now(clock)
                : LocalDate.of(year, 1, 1);
        long workingDays = employees.workScheduleOn(employeeId, reference)
                .map(s -> EnumSet.allOf(DayOfWeek.class).stream().filter(s::isWorkingDay).count())
                .orElse(5L);
        return FULL_ENTITLEMENT.multiply(BigDecimal.valueOf(workingDays)).divide(BigDecimal.valueOf(5), 1,
                RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
    }
}
