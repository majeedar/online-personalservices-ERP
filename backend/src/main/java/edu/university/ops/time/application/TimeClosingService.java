package edu.university.ops.time.application;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeMonthClosing;
import edu.university.ops.time.domain.TimeRepositories.TimeCorrectionRepository;
import edu.university.ops.time.domain.TimeRepositories.TimeMonthClosingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Monthly closing of time accounts. Closing recalculates every active employee's
 * days of the month one final time and freezes them; afterwards corrections and
 * backdated bookings for that month are rejected until it is reopened.
 */
@Service
@Transactional
public class TimeClosingService {

    public record MonthStatus(YearMonth month, boolean closed, Instant closedAt, String closedBy, int employees,
                              long pendingCorrections, boolean closable, String blockedReason) {
    }

    private final TimeMonthClosingRepository closings;
    private final TimeCorrectionRepository corrections;
    private final TimeAccountService accounts;
    private final EmployeeDirectory employees;
    private final AuditService audit;
    private final Clock clock;

    TimeClosingService(TimeMonthClosingRepository closings, TimeCorrectionRepository corrections,
                       TimeAccountService accounts, EmployeeDirectory employees, AuditService audit, Clock clock) {
        this.closings = closings;
        this.corrections = corrections;
        this.accounts = accounts;
        this.employees = employees;
        this.audit = audit;
        this.clock = clock;
    }

    public TimeMonthClosing close(YearMonth month, String closedBy) {
        blockedReason(month).ifPresent(reason -> {
            throw new BusinessException(isClosed(month) ? ErrorCode.TIME_MONTH_CLOSED : ErrorCode.TIME_MONTH_NOT_CLOSABLE,
                    reason);
        });
        int employeeCount = 0;
        int days = 0;
        for (var e : employees.activeEmployees()) {
            days += accounts.closeMonth(e.id(), month);
            employeeCount++;
        }
        TimeMonthClosing closing;
        try {
            closing = closings.saveAndFlush(new TimeMonthClosing(month, closedBy, employeeCount, days,
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.TIME_MONTH_CLOSED, month + " has just been closed by someone else.");
        }
        audit.record("TIME_MONTH_CLOSED", "TimeMonthClosing", closing.getId(), null,
                Map.of("month", month.toString(), "employees", employeeCount, "days", days));
        return closing;
    }

    public TimeMonthClosing reopen(YearMonth month, String reopenedBy, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Please give a reason for reopening the month.");
        }
        TimeMonthClosing closing = closings.findByYearMonthAndStatus(month.toString(), TimeMonthClosing.Status.CLOSED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, month + " is not closed."));
        closing.reopen(reopenedBy, reason.strip(), Instant.now(clock));
        closings.saveAndFlush(closing);
        employees.activeEmployees().forEach(e -> accounts.reopenMonth(e.id(), month));
        audit.record("TIME_MONTH_REOPENED", "TimeMonthClosing", closing.getId(), Map.of("status", "CLOSED"),
                Map.of("month", month.toString(), "status", "REOPENED", "reasonGiven", true));
        return closing;
    }

    /** The last {@code months} months (newest first), including the current, still open one. */
    @Transactional(readOnly = true)
    public List<MonthStatus> overview(int months) {
        YearMonth current = YearMonth.now(clock);
        List<MonthStatus> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth m = current.minusMonths(i);
            Optional<TimeMonthClosing> closing = closings.findByYearMonthAndStatus(m.toString(),
                    TimeMonthClosing.Status.CLOSED);
            Optional<String> blocked = blockedReason(m);
            result.add(new MonthStatus(m, closing.isPresent(), closing.map(TimeMonthClosing::getClosedAt).orElse(null),
                    closing.map(TimeMonthClosing::getClosedBy).orElse(null),
                    closing.map(TimeMonthClosing::getEmployees).orElse(0), pending(m), blocked.isEmpty(),
                    closing.isPresent() ? null : blocked.orElse(null)));
        }
        return result;
    }

    public boolean isClosed(YearMonth month) {
        return closings.findByYearMonthAndStatus(month.toString(), TimeMonthClosing.Status.CLOSED).isPresent();
    }

    /** Why the month cannot be closed now, if it cannot. */
    private Optional<String> blockedReason(YearMonth month) {
        if (isClosed(month)) {
            return Optional.of(month + " is already closed.");
        }
        if (!month.isBefore(YearMonth.now(clock))) {
            return Optional.of("Only past months can be closed.");
        }
        long pending = pending(month);
        if (pending > 0) {
            return Optional.of(pending + " time correction(s) in " + month + " are still waiting for a decision.");
        }
        return Optional.empty();
    }

    private long pending(YearMonth month) {
        return corrections.countByStatusAndDateBetween(TimeCorrectionRequest.Status.IN_APPROVAL, month.atDay(1),
                month.atEndOfMonth());
    }
}
