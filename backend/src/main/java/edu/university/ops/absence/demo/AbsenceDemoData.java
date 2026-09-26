package edu.university.ops.absence.demo;

import edu.university.ops.absence.application.AbsenceService;
import edu.university.ops.absence.application.AbsenceService.AbsenceInput;
import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveTypeRepository;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.RunAs;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * DEMO DATA ONLY (ADR-009). Creates leave requests through the real application
 * services, relative to today, so the demo always looks current and every seeded
 * request satisfies the business rules. Runs once (skipped if requests exist).
 */
@Component
@Profile("demo")
@Order(10)
class AbsenceDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AbsenceDemoData.class);

    private final AbsenceRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;
    private final AbsenceService absences;
    private final RunAs runAs;
    private final Clock clock;

    AbsenceDemoData(AbsenceRequestRepository requests, LeaveTypeRepository leaveTypes, AbsenceService absences,
                    RunAs runAs, Clock clock) {
        this.requests = requests;
        this.leaveTypes = leaveTypes;
        this.absences = absences;
        this.runAs = runAs;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (requests.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate lastMonday = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));

        // Approved in the past and upcoming (visible on dashboards and the team calendar).
        seed("employee", "ANNUAL_LEAVE", lastMonday.minusWeeks(3), lastMonday.minusWeeks(3).plusDays(2), "supervisor");
        seed("cbeispiel", "ANNUAL_LEAVE", nextMonday.plusWeeks(1), nextMonday.plusWeeks(1).plusDays(4), "supervisor");
        seed("jdemo", "FLEX_DAY", nextMonday.plusDays(4), nextMonday.plusDays(4), "supervisor");
        // Pending: approval tasks for 'supervisor' and 'supervisor2'.
        seed("employee", "ANNUAL_LEAVE", nextMonday.plusWeeks(4), nextMonday.plusWeeks(4).plusDays(4), null);
        seed("parttime", "ANNUAL_LEAVE", nextMonday.plusWeeks(2).plusDays(3), nextMonday.plusWeeks(3), null);
        seed("eprobe", "ANNUAL_LEAVE", nextMonday.plusWeeks(3).plusDays(1), nextMonday.plusWeeks(3).plusDays(2), null);
        seed("oplatzhalter", "ANNUAL_LEAVE", nextMonday.plusWeeks(2), nextMonday.plusWeeks(2).plusDays(1), null);
        // Sick leave needs no approval.
        seed("dmuster", "SICK_LEAVE", lastMonday.plusDays(1), lastMonday.plusDays(2), null);
        log.info("Demo absence requests created");
    }

    private void seed(String username, String leaveTypeCode, LocalDate from, LocalDate to, String approver) {
        try {
            OpsPrincipal employee = runAs.principal(username);
            var type = leaveTypes.findByCode(leaveTypeCode).orElseThrow();
            AbsenceRequest request = runAs.call(employee, () -> {
                AbsenceRequest draft = absences.createDraft(employee,
                        new AbsenceInput(type.getId(), from, to, null, null));
                return absences.submit(draft.getId(), employee);
            });
            if (approver != null) {
                OpsPrincipal decider = runAs.principal(approver);
                runAs.call(decider, () -> absences.decide(request.getId(), Decision.APPROVE, null, decider));
            }
        } catch (RuntimeException e) {
            log.warn("Demo absence for {} {}..{} skipped: {}", username, from, to, e.getMessage());
        }
    }
}
