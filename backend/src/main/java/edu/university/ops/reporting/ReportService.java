package edu.university.ops.reporting;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.absence.AbsenceReports;
import edu.university.ops.absence.AbsenceReports.LeaveUsage;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.employee.EmployeeDirectory.EmployeeSummary;
import edu.university.ops.organisation.OrganisationDirectory;
import edu.university.ops.organisation.OrganisationDirectory.OrganisationUnitSummary;
import edu.university.ops.shared.batch.BatchJobRun;
import edu.university.ops.shared.batch.BatchJobService;
import edu.university.ops.shared.integration.IntegrationError;
import edu.university.ops.shared.integration.IntegrationMonitor;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.time.TimeAccounts;
import edu.university.ops.travel.TravelReports;
import edu.university.ops.travel.TravelReports.TravelSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Report queries (AGENT.md §81). Each returns a header plus rows, so every report can be exported as CSV. */
@Service
public class ReportService {

    /** Title, columns and {@link Text} cells are rendered in the reader's language by the controller. */
    public record Report(Text title, List<Text> columns, List<List<Object>> rows) {
    }

    private static List<Text> columns(String... names) {
        return java.util.Arrays.stream(names).map(Text::of).toList();
    }

    private final AbsenceReports absences;
    private final TravelReports travel;
    private final TimeAccounts time;
    private final EmployeeDirectory employees;
    private final OrganisationDirectory organisations;
    private final WorkflowService workflow;
    private final IntegrationMonitor integrations;
    private final BatchJobService batch;
    private final Clock clock;

    ReportService(AbsenceReports absences, TravelReports travel, TimeAccounts time, EmployeeDirectory employees,
                  OrganisationDirectory organisations, WorkflowService workflow, IntegrationMonitor integrations,
                  BatchJobService batch, Clock clock) {
        this.absences = absences;
        this.travel = travel;
        this.time = time;
        this.employees = employees;
        this.organisations = organisations;
        this.workflow = workflow;
        this.integrations = integrations;
        this.batch = batch;
        this.clock = clock;
    }

    /** Aggregated per unit; no individual data (data minimisation). */
    public Report leaveUsageByUnit(int year) {
        Map<UUID, EmployeeSummary> staff = employees.activeEmployees().stream()
                .collect(Collectors.toMap(EmployeeSummary::id, Function.identity()));
        Map<UUID, OrganisationUnitSummary> units = organisations.allUnits().stream()
                .collect(Collectors.toMap(OrganisationUnitSummary::id, Function.identity()));
        Map<String, BigDecimal[]> totals = new TreeMap<>();
        Map<String, Integer> headcount = new TreeMap<>();
        for (LeaveUsage u : absences.leaveUsage(year)) {
            EmployeeSummary e = staff.get(u.employeeId());
            if (e == null) {
                continue;
            }
            OrganisationUnitSummary unit = units.get(e.organisationUnitId());
            String key = unit == null ? "?" : unit.code() + " – " + unit.name();
            BigDecimal[] t = totals.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(u.entitled());
            t[1] = t[1].add(u.used());
            t[2] = t[2].add(u.reserved());
            t[3] = t[3].add(u.remaining());
            headcount.merge(key, 1, Integer::sum);
        }
        List<List<Object>> rows = new ArrayList<>();
        totals.forEach((unit, t) -> rows.add(List.of(unit, headcount.get(unit), t[0], t[1], t[2], t[3],
                t[0].signum() == 0 ? BigDecimal.ZERO
                        : t[1].multiply(BigDecimal.valueOf(100)).divide(t[0], 1, java.math.RoundingMode.HALF_UP))));
        return new Report(Text.of("Leave usage by organisational unit {year}", "year", year),
                columns("Unit", "Employees", "Entitled days", "Used", "Reserved", "Remaining", "Used %"), rows);
    }

    public Report pendingApprovals() {
        Instant now = Instant.now(clock);
        List<List<Object>> rows = workflow.allOpenTasks().stream()
                .sorted(Comparator.comparing(t -> t.createdAt()))
                .map(t -> List.<Object>of(t.titleText(), Text.of(t.stepType()),
                        t.assignedEmployeeName() != null ? t.assignedEmployeeName()
                                : Text.of("Role {role}", "role", Text.of(t.assignedRole())),
                        t.createdAt().atZone(clock.getZone()).toLocalDate(),
                        Duration.between(t.createdAt(), now).toDays(),
                        Text.of(t.dueDate() != null && t.dueDate().isBefore(LocalDate.now(clock)) ? "overdue"
                                : "on time")))
                .toList();
        return new Report(Text.of("Pending approvals"), columns("Task", "Step", "Assigned to", "Since", "Days open",
                "Due"), rows);
    }

    public Report travelByStatus() {
        Map<String, List<TravelSummary>> byStatus = travel.allTrips().stream()
                .collect(Collectors.groupingBy(TravelSummary::status, TreeMap::new, Collectors.toList()));
        List<List<Object>> rows = new ArrayList<>();
        byStatus.forEach((status, trips) -> rows.add(List.of(Text.of(status), trips.size(),
                trips.stream().map(TravelSummary::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add))));
        return new Report(Text.of("Travel requests by status"), columns("Status", "Trips", "Estimated cost (EUR)"),
                rows);
    }

    public Report travelEstimatedVsActual() {
        Map<UUID, String> names = employees.activeEmployees().stream()
                .collect(Collectors.toMap(EmployeeSummary::id, EmployeeSummary::displayName));
        List<List<Object>> rows = travel.allTrips().stream()
                .filter(t -> t.settledAmount() != null)
                .sorted(Comparator.comparing(TravelSummary::start))
                .map(t -> List.<Object>of(names.getOrDefault(t.employeeId(), "—"), t.destinationCity(),
                        t.start().atZone(clock.getZone()).toLocalDate(), t.costCentre(), t.estimatedCost(),
                        t.settledAmount(), t.settledAmount().subtract(t.estimatedCost()), t.currency()))
                .toList();
        return new Report(Text.of("Travel: estimated vs actual cost"), columns("Traveller", "Destination", "Start",
                "Cost centre", "Estimated", "Actual", "Difference", "Currency"), rows);
    }

    public Report monthlyWorkingTime(YearMonth month) {
        Map<UUID, String> unitNames = organisations.allUnits().stream()
                .collect(Collectors.toMap(OrganisationUnitSummary::id, OrganisationUnitSummary::code));
        List<List<Object>> rows = employees.activeEmployees().stream()
                .sorted(Comparator.comparing(EmployeeSummary::displayName))
                .map(e -> {
                    var s = time.monthSummary(e.id(), month);
                    return List.<Object>of(e.personnelNumber(), e.displayName(),
                            unitNames.getOrDefault(e.organisationUnitId(), "—"), hours(s.targetMinutes()),
                            hours(s.workedMinutes()), hours(s.absenceMinutes()), hours(s.balanceMinutes()),
                            s.incompleteDays());
                }).toList();
        return new Report(Text.of("Monthly working-time overview {month}", "month", month.toString()),
                columns("Personnel no.", "Name", "Unit",
                "Target h", "Worked h", "Absence h", "Balance h", "Incomplete days"), rows);
    }

    public Report failedIntegrations() {
        List<List<Object>> rows = integrations.errors(true, PageRequest.of(0, 500)).getContent().stream()
                .sorted(Comparator.comparing(IntegrationError::getCreatedAt).reversed())
                .map(e -> List.<Object>of(e.getCreatedAt().atZone(clock.getZone()).toLocalDateTime().withNano(0),
                        e.getErrorCode(), nullToDash(e.getExternalReference()), e.getErrorMessage(),
                        e.getRetryCount()))
                .toList();
        return new Report(Text.of("Unresolved integration errors"), columns("Time", "Code", "Reference", "Message",
                "Retries"), rows);
    }

    public Report failedBatchJobs() {
        List<List<Object>> rows = batch.history(null, PageRequest.of(0, 500)).getContent().stream()
                .filter(r -> r.getStatus() == BatchJobRun.Status.FAILED || r.getStatus() == BatchJobRun.Status.PARTIAL)
                .map(r -> List.<Object>of(r.getStartedAt().atZone(clock.getZone()).toLocalDateTime().withNano(0),
                        r.getJobName(), Text.of(r.getStatus().name()), r.getProcessedRecords(), r.getFailedRecords(),
                        nullToDash(r.getStartedBy())))
                .toList();
        return new Report(Text.of("Failed and partial batch runs"), columns("Started", "Job", "Status", "Processed",
                "Failed", "Started by"), rows);
    }

    /** Available reports and who may see them (least privilege, AGENT.md §82). */
    public static Map<String, Text> catalogue() {
        Map<String, Text> m = new LinkedHashMap<>();
        m.put("leave-usage", Text.of("Leave usage by organisational unit"));
        m.put("pending-approvals", Text.of("Pending approvals"));
        m.put("travel-by-status", Text.of("Travel requests by status"));
        m.put("travel-costs", Text.of("Travel: estimated vs actual cost"));
        m.put("working-time", Text.of("Monthly working-time overview"));
        m.put("failed-integrations", Text.of("Unresolved integration errors"));
        m.put("failed-batch-jobs", Text.of("Failed batch jobs"));
        return m;
    }

    private static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    private static String nullToDash(String s) {
        return s == null ? "—" : s;
    }
}
