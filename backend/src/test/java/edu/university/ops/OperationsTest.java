package edu.university.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import edu.university.ops.support.TestClockConfiguration.MutableClock;
import java.time.Duration;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration and batch acceptance criteria (AGENT.md §72, §28), reports (§81)
 * and admin security (§66).
 */
@IntegrationTest
class OperationsTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    ApiClient admin;

    @BeforeEach
    void login() throws Exception {
        admin = ApiClient.login(mvc, json, "erpadmin");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    JsonNode runJob(String name) throws Exception {
        return admin.post("/api/v1/admin/jobs/" + name + "/run", null).expect(200).body();
    }

    @Test
    void organisationAndEmployeeSyncMapValidateAndUpsert() throws Exception {
        JsonNode orgRun = runJob("organisation-sync");
        assertThat(orgRun.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(orgRun.get("failedRecords").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM organisation_unit WHERE code = 'INST-A3'", String.class))
                .isEqualTo("Institute of Mathematics");
        JsonNode orgErrors = admin.get("/api/v1/admin/batch-runs/" + orgRun.get("id").asText() + "/errors").body();
        assertThat(orgErrors.findValuesAsText("recordReference")).containsExactly("LAB-X");

        JsonNode employeeRun = runJob("employee-sync");
        assertThat(employeeRun.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(employeeRun.get("failedRecords").asInt()).isEqualTo(2);
        // New employee created with employment, schedule and EMPLOYEE role.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee e JOIN employment m ON m.employee_id = e.id "
                + "JOIN user_role r ON r.employee_id = e.id WHERE e.personnel_number = 'P10019'", Integer.class))
                .isEqualTo(1);
        // Changed e-mail applied; employee who left deactivated (never deleted).
        assertThat(jdbc.queryForObject("SELECT email FROM employee WHERE personnel_number = 'P10012'", String.class))
                .isEqualTo("c.beispiel@uni.example");
        assertThat(jdbc.queryForObject("SELECT active FROM employee WHERE personnel_number = 'P10017'", Boolean.class))
                .isFalse();
        // Invalid records were not written.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee WHERE personnel_number IN ('P10020', 'P10021')",
                Integer.class)).isZero();
        JsonNode errors = admin.get("/api/v1/admin/integration-errors").body().get("items");
        assertThat(errors.findValuesAsText("externalReference")).contains("P10020", "P10021");

        // Idempotent: the next run finds no changes.
        JsonNode rerun = runJob("employee-sync");
        assertThat(rerun.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(rerun.get("processedRecords").asInt()).isZero();

        runJob("supervisor-sync");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_relation r JOIN employee e ON e.id = r.employee_id "
                + "WHERE e.personnel_number = 'P10019' AND r.approver_id = '20000000-0000-0000-0000-000000000002'",
                Integer.class)).isEqualTo(3);

        runJob("work-schedule-sync");
        assertThat(jdbc.queryForObject("SELECT weekly_target_minutes FROM work_schedule s JOIN employee e "
                + "ON e.id = s.employee_id WHERE e.personnel_number = 'P10016' AND s.valid_to IS NULL",
                Integer.class)).isEqualTo(1800);

        JsonNode integrationRuns = admin.get("/api/v1/admin/integration-runs").body().get("items");
        assertThat(integrationRuns.findValuesAsText("interfaceName")).contains("PERSONNEL_EMPLOYEE_SYNC",
                "PERSONNEL_ORGANISATION_SYNC");
    }

    @Test
    void entitlementAndTimeJobsAreConsistentAndIdempotent() throws Exception {
        String sql = "SELECT used_days + reserved_days FROM leave_entitlement l JOIN employee e ON e.id = l.employee_id "
                + "WHERE e.username = 'parttime' AND l.year = 2026";
        Double before = jdbc.queryForObject(sql, Double.class);
        JsonNode run = runJob("leave-entitlement-calculation");
        assertThat(run.get("status").asText()).isEqualTo("SUCCESS");
        // The ledger kept by the absence workflow matches the recalculation from absence days.
        assertThat(jdbc.queryForObject(sql, Double.class)).isEqualTo(before);

        assertThat(runJob("time-account-recalculation").get("status").asText()).isEqualTo("SUCCESS");
        assertThat(runJob("integration-retry").get("status").asText()).isIn("SUCCESS", "PARTIAL");
    }

    @Test
    void remindersAreSentOncePerDay() throws Exception {
        clock.advance(Duration.ofDays(4));
        JsonNode first = runJob("workflow-reminder");
        assertThat(first.get("processedRecords").asInt()).isPositive();
        Integer reminders = jdbc.queryForObject("SELECT count(*) FROM notification WHERE type = 'TASK_REMINDER'",
                Integer.class);
        runJob("workflow-reminder");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE type = 'TASK_REMINDER'",
                Integer.class)).isEqualTo(reminders);
    }

    @Test
    void batchHistoryAndHealthAreVisibleToOperators() throws Exception {
        runJob("time-account-recalculation");
        JsonNode jobs = admin.get("/api/v1/admin/batch-jobs").expect(200).body();
        assertThat(jobs.findValuesAsText("name")).contains("employee-sync", "organisation-sync", "supervisor-sync",
                "work-schedule-sync", "leave-entitlement-calculation", "time-account-recalculation",
                "workflow-reminder", "integration-retry");
        JsonNode health = admin.get("/api/v1/admin/system-health").expect(200).body();
        assertThat(health.at("/components/Database").asText()).isEqualTo("UP");
        assertThat(admin.get("/api/v1/admin/audit").expect(200).body().get("items").size()).isPositive();
    }

    @Test
    void adminAndReportAccessFollowsLeastPrivilege() throws Exception {
        ApiClient auditor = ApiClient.login(mvc, json, "auditor");
        assertThat(auditor.get("/api/v1/admin/audit").status()).isEqualTo(200);
        assertThat(auditor.post("/api/v1/admin/jobs/employee-sync/run", null).status()).isEqualTo(403);
        assertThat(auditor.get("/api/v1/reports/working-time").status()).isEqualTo(403);

        ApiClient employee = ApiClient.login(mvc, json, "employee");
        assertThat(employee.get("/api/v1/admin/batch-runs").status()).isEqualTo(403);
        assertThat(employee.get("/api/v1/reports/leave-usage").status()).isEqualTo(403);

        ApiClient hr = ApiClient.login(mvc, json, "hradmin");
        JsonNode report = hr.get("/api/v1/reports/leave-usage?year=2026").expect(200).body();
        assertThat(report.get("rows").size()).isPositive();
        var csv = hr.get("/api/v1/reports/working-time?month=2026-09&format=csv").expect(200);
        String text = csv.result().getResponse().getContentAsString();
        assertThat(csv.result().getResponse().getContentType()).startsWith("text/csv");
        assertThat(text).contains("Personnel no.,Name,Unit");
        JsonNode available = StreamSupport.stream(hr.get("/api/v1/reports").body().spliterator(), false)
                .map(r -> r.get("id")).collect(json::createArrayNode, (a, v) -> a.add(v), (a, b) -> a.addAll(b));
        assertThat(available.toString()).contains("leave-usage", "working-time").doesNotContain("failed-batch-jobs");
    }
}
