package edu.university.ops.time.api;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.time.application.TimeClosingService;
import edu.university.ops.time.application.TimeClosingService.MonthStatus;
import edu.university.ops.time.domain.TimeMonthClosing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Monthly closing of time accounts (TIME_ADMIN, HR_ADMIN). */
@RestController
@RequestMapping("/api/v1/time/closings")
@Tag(name = "Time")
class TimeClosingController {

    private final TimeClosingService closings;

    TimeClosingController(TimeClosingService closings) {
        this.closings = closings;
    }

    record ReopenBody(@Size(max = 1000) String reason) {
    }

    record ClosingResponse(String month, String status, int employees, int days) {
        static ClosingResponse of(TimeMonthClosing c) {
            return new ClosingResponse(c.getYearMonth(), c.getStatus().name(), c.getEmployees(), c.getDays());
        }
    }

    @GetMapping
    @Operation(summary = "Closing status of the last 12 months")
    List<MonthStatus> overview() {
        requireTimeAdmin();
        return closings.overview(12);
    }

    @PostMapping("/{month}/close")
    @Operation(summary = "Close a past month (no pending corrections allowed)")
    ClosingResponse close(@PathVariable String month) {
        OpsPrincipal me = requireTimeAdmin();
        return ClosingResponse.of(closings.close(parse(month), me.username()));
    }

    @PostMapping("/{month}/reopen")
    @Operation(summary = "Reopen a closed month (reason required)")
    ClosingResponse reopen(@PathVariable String month, @Valid @RequestBody ReopenBody body) {
        OpsPrincipal me = requireTimeAdmin();
        return ClosingResponse.of(closings.reopen(parse(month), me.username(), body.reason()));
    }

    private static OpsPrincipal requireTimeAdmin() {
        OpsPrincipal me = CurrentUser.require();
        if (!me.hasAnyRole(Role.TIME_ADMIN, Role.HR_ADMIN)) {
            throw BusinessException.forbidden();
        }
        return me;
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Month must be given as YYYY-MM.");
        }
    }
}
