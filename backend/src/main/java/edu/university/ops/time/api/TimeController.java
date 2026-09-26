package edu.university.ops.time.api;

import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import edu.university.ops.time.application.TimeAccountService;
import edu.university.ops.time.application.TimeAccountService.DayView;
import edu.university.ops.time.application.TimeClockService;
import edu.university.ops.time.application.TimeCorrectionService;
import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.time.domain.TimeSequence.State;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Time API (AGENT.md §37). */
@RestController
@RequestMapping("/api/v1/time")
@Tag(name = "Time")
class TimeController {

    private final TimeClockService clockService;
    private final TimeAccountService accounts;
    private final TimeCorrectionService correctionService;
    private final WorkflowService workflow;
    private final Clock clock;

    TimeController(TimeClockService clockService, TimeAccountService accounts,
                   TimeCorrectionService correctionService, WorkflowService workflow, Clock clock) {
        this.clockService = clockService;
        this.accounts = accounts;
        this.correctionService = correctionService;
        this.workflow = workflow;
        this.clock = clock;
    }

    record EntryResponse(UUID id, Instant timestamp, TimeEntry.Type type, TimeEntry.Source source, boolean voided,
                         UUID correctionRequestId) {
        static EntryResponse of(TimeEntry e) {
            return new EntryResponse(e.getId(), e.getTimestamp(), e.getType(), e.getSource(), e.isVoided(),
                    e.getCorrectionRequestId());
        }
    }

    record TodayResponse(LocalDate date, State state, Set<TimeEntry.Type> allowedActions, List<EntryResponse> entries,
                         DayView account) {
    }

    record Totals(int targetMinutes, int workedMinutes, int creditedMinutes, int balanceMinutes) {
        static Totals of(List<DayView> days) {
            return new Totals(days.stream().filter(d -> !d.future()).mapToInt(DayView::targetMinutes).sum(),
                    days.stream().mapToInt(DayView::workedMinutes).sum(),
                    days.stream().mapToInt(DayView::creditedMinutes).sum(),
                    days.stream().mapToInt(DayView::balanceMinutes).sum());
        }
    }

    record MonthResponse(int year, int month, List<DayView> days, Totals totals) {
    }

    record BalanceResponse(int monthBalanceMinutes, int yearBalanceMinutes) {
    }

    record CorrectionBody(@NotNull LocalDate date, @NotNull TimeCorrectionRequest.Operation operation,
                          UUID originalEntryId, LocalTime requestedTime, TimeEntry.Type requestedType,
                          @NotBlank @Size(max = 1000) String reason) {
    }

    record DecisionBody(@Size(max = 1000) String comment) {
    }

    record CorrectionResponse(UUID id, LocalDate date, TimeCorrectionRequest.Operation operation,
                              UUID originalEntryId, Instant requestedTimestamp, TimeEntry.Type requestedType,
                              String reason, TimeCorrectionRequest.Status status, Instant createdAt,
                              Instant decidedAt, List<InstanceHistory> history, UUID actionableTaskId) {
    }

    // ------------------------------------------------------------------ clock

    @GetMapping("/today")
    @Operation(summary = "Today's entries, current state, allowed next actions and live account")
    TodayResponse today() {
        return todayOf(CurrentUser.require().employeeId());
    }

    @PostMapping("/clock-in")
    TodayResponse clockIn() {
        return act(TimeEntry.Type.CLOCK_IN);
    }

    @PostMapping("/clock-out")
    TodayResponse clockOut() {
        return act(TimeEntry.Type.CLOCK_OUT);
    }

    @PostMapping("/break-start")
    TodayResponse breakStart() {
        return act(TimeEntry.Type.BREAK_START);
    }

    @PostMapping("/break-end")
    TodayResponse breakEnd() {
        return act(TimeEntry.Type.BREAK_END);
    }

    @GetMapping("/entries")
    @Operation(summary = "All entries of a day including voided ones (history)")
    List<EntryResponse> entries(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return clockService.history(CurrentUser.require().employeeId(), date).stream().map(EntryResponse::of).toList();
    }

    // --------------------------------------------------------------- accounts

    @GetMapping("/month/{year}/{month}")
    @Operation(summary = "Daily accounts and totals of a month")
    MonthResponse month(@PathVariable int year, @PathVariable int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<DayView> days = accounts.computeRange(CurrentUser.require().employeeId(), ym.atDay(1), ym.atEndOfMonth());
        return new MonthResponse(year, month, days, Totals.of(days));
    }

    @GetMapping("/balance")
    @Operation(summary = "Working-time balance of the current month and year to date")
    BalanceResponse balance() {
        UUID me = CurrentUser.require().employeeId();
        LocalDate today = LocalDate.now(clock);
        return new BalanceResponse(accounts.balanceMinutes(me, today.withDayOfMonth(1), today),
                accounts.balanceMinutes(me, today.withDayOfYear(1), today));
    }

    // ------------------------------------------------------------ corrections

    @PostMapping("/corrections")
    @Operation(summary = "Request a correction (ADD a missing entry, MODIFY or DELETE one)")
    CorrectionResponse requestCorrection(@Valid @RequestBody CorrectionBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(correctionService.request(me, new TimeCorrectionService.CorrectionInput(body.date(),
                body.operation(), body.originalEntryId(), body.requestedTime(), body.requestedType(), body.reason())),
                me);
    }

    @GetMapping("/corrections")
    List<CorrectionResponse> corrections() {
        OpsPrincipal me = CurrentUser.require();
        return correctionService.mine(me.employeeId()).stream().map(c -> toResponse(c, me)).toList();
    }

    @GetMapping("/corrections/{id}")
    CorrectionResponse correction(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(correctionService.visible(id, me), me);
    }

    @PostMapping("/corrections/{id}/approve")
    CorrectionResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        return decide(id, Decision.APPROVE, body);
    }

    @PostMapping("/corrections/{id}/reject")
    CorrectionResponse reject(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return decide(id, Decision.REJECT, body);
    }

    private CorrectionResponse decide(UUID id, Decision decision, DecisionBody body) {
        OpsPrincipal me = CurrentUser.require();
        correctionService.decide(id, decision, body == null ? null : body.comment(), me);
        return toResponse(correctionService.visible(id, me), me);
    }

    private TodayResponse act(TimeEntry.Type type) {
        UUID me = CurrentUser.require().employeeId();
        clockService.record(me, type);
        return todayOf(me);
    }

    private TodayResponse todayOf(UUID employeeId) {
        var day = clockService.today(employeeId);
        return new TodayResponse(day.date(), day.state(), day.allowedNext(),
                day.entries().stream().map(EntryResponse::of).toList(), accounts.compute(employeeId, day.date()));
    }

    private CorrectionResponse toResponse(TimeCorrectionRequest c, OpsPrincipal me) {
        UUID taskId = c.getWorkflowInstanceId() == null ? null
                : workflow.actionableTask(c.getWorkflowInstanceId(), me).map(t -> t.id()).orElse(null);
        return new CorrectionResponse(c.getId(), c.getDate(), c.getOperation(), c.getOriginalTimeEntryId(),
                c.getRequestedTimestamp(), c.getRequestedType(), c.getReason(), c.getStatus(), c.getCreatedAt(),
                c.getDecidedAt(), workflow.history(TimeCorrectionService.BUSINESS_OBJECT_TYPE, c.getId()), taskId);
    }
}
