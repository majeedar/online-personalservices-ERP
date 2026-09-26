package edu.university.ops.absence.api;

import edu.university.ops.absence.api.AbsenceDtos.LeaveBalanceResponse;
import edu.university.ops.absence.api.AbsenceDtos.LeaveTypeResponse;
import edu.university.ops.absence.api.AbsenceDtos.PersonRef;
import edu.university.ops.absence.api.AbsenceDtos.TeamAbsenceResponse;
import edu.university.ops.absence.application.AbsenceQueryService;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Absence")
class LeaveController {

    private final AbsenceQueryService queries;
    private final Clock clock;

    LeaveController(AbsenceQueryService queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping("/leave-types")
    @Operation(summary = "Leave types that can be requested")
    List<LeaveTypeResponse> leaveTypes() {
        return queries.activeLeaveTypes().stream().map(LeaveTypeResponse::of).toList();
    }

    @GetMapping("/leave-balances")
    @Operation(summary = "The caller's leave balances for a year (default: current year)")
    List<LeaveBalanceResponse> balances(@RequestParam(required = false) Integer year) {
        int y = year != null ? year : LocalDate.now(clock).getYear();
        Map<UUID, LeaveType> types = queries.leaveTypesById();
        return queries.balances(CurrentUser.require().employeeId(), y).stream()
                .map(e -> LeaveBalanceResponse.of(e, types.get(e.getLeaveTypeId()))).toList();
    }

    @GetMapping("/team/absences")
    @Operation(summary = "Absences of employees the caller approves (default: next 8 weeks)")
    List<TeamAbsenceResponse> team(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusWeeks(8);
        return queries.teamAbsences(CurrentUser.require(), start, end).stream()
                .map(t -> new TeamAbsenceResponse(t.request().getId(),
                        new PersonRef(t.employee().id(), t.employee().displayName()), t.request().getStartDate(),
                        t.request().getEndDate(), t.request().getStatus(), t.request().workingDays()))
                .toList();
    }
}
