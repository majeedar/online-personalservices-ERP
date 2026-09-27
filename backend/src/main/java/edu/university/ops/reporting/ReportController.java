package edu.university.ops.reporting;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.reporting.ReportService.Report;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports as JSON or CSV (AGENT.md §81). */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
class ReportController {

    /** Who may run which report; personal working-time data only for HR and time admins. */
    static final Map<String, Set<Role>> ACCESS = Map.of(
            "leave-usage", EnumSet.of(Role.HR_ADMIN, Role.AUDITOR),
            "pending-approvals", EnumSet.of(Role.HR_ADMIN, Role.ERP_ADMIN, Role.SUPPORT),
            "travel-by-status", EnumSet.of(Role.TRAVEL_OFFICE, Role.FINANCIAL_APPROVER, Role.AUDITOR),
            "travel-costs", EnumSet.of(Role.TRAVEL_OFFICE, Role.FINANCIAL_APPROVER, Role.AUDITOR),
            "working-time", EnumSet.of(Role.HR_ADMIN, Role.TIME_ADMIN),
            "failed-integrations", EnumSet.of(Role.ERP_ADMIN, Role.SUPPORT, Role.AUDITOR),
            "failed-batch-jobs", EnumSet.of(Role.ERP_ADMIN, Role.SUPPORT, Role.AUDITOR));

    private final ReportService reports;
    private final Clock clock;

    ReportController(ReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    record ReportInfo(String id, String title) {
    }

    @GetMapping
    @Operation(summary = "Reports available to the caller")
    List<ReportInfo> available() {
        OpsPrincipal me = CurrentUser.require();
        return ReportService.catalogue().entrySet().stream()
                .filter(e -> ACCESS.get(e.getKey()).stream().anyMatch(me::hasRole))
                .map(e -> new ReportInfo(e.getKey(), e.getValue().render())).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Run a report; format=csv downloads it")
    ResponseEntity<?> run(@PathVariable String id, @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) String month,
                          @RequestParam(defaultValue = "json") String format) {
        OpsPrincipal me = CurrentUser.require();
        Set<Role> allowed = ACCESS.get(id);
        if (allowed == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Text.of("Report {id} was not found.", "id", id));
        }
        if (allowed.stream().noneMatch(me::hasRole)) {
            throw BusinessException.forbidden();
        }
        LocalDate today = LocalDate.now(clock);
        Report report = switch (id) {
            case "leave-usage" -> reports.leaveUsageByUnit(year != null ? year : today.getYear());
            case "pending-approvals" -> reports.pendingApprovals();
            case "travel-by-status" -> reports.travelByStatus();
            case "travel-costs" -> reports.travelEstimatedVsActual();
            case "working-time" -> reports.monthlyWorkingTime(month != null ? YearMonth.parse(month)
                    : YearMonth.from(today));
            case "failed-integrations" -> reports.failedIntegrations();
            case "failed-batch-jobs" -> reports.failedBatchJobs();
            default -> throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Text.of("Report {id} was not found.", "id", id));
        };
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(id + ".csv").build().toString())
                    .body(toCsv(rendered(report)));
        }
        return ResponseEntity.ok(rendered(report));
    }

    /** A report as sent: title, columns and text cells in the reader's language (ADR-020). */
    record RenderedReport(String title, List<String> columns, List<List<Object>> rows) {
    }

    static RenderedReport rendered(Report report) {
        return new RenderedReport(report.title().render(), report.columns().stream().map(Text::render).toList(),
                report.rows().stream().map(row -> row.stream()
                        .map(cell -> cell instanceof Text t ? (Object) t.render() : cell).toList()).toList());
    }

    /** RFC 4180 CSV with a BOM so spreadsheet tools detect UTF-8; formula injection is neutralised. */
    static String toCsv(RenderedReport report) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(report.columns().stream().map(ReportController::cell).collect(Collectors.joining(","))).append("\r\n");
        for (List<Object> row : report.rows()) {
            sb.append(row.stream().map(ReportController::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        return sb.toString();
    }

    private static String cell(Object value) {
        String s = value == null ? "" : value.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?[0-9.]+")) {
            s = "'" + s;
        }
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
