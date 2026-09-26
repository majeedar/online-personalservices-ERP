package edu.university.ops.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Monthly closing of time accounts. Test "now": Monday 2026-09-21. */
@IntegrationTest
class TimeClosingTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient timeAdmin;
    ApiClient employee;

    @BeforeEach
    void login() throws Exception {
        timeAdmin = ApiClient.login(mvc, json, "timeadmin");
        employee = ApiClient.login(mvc, json, "employee");
    }

    void reopenIfClosed(String month) throws Exception {
        timeAdmin.post("/api/v1/time/closings/" + month + "/reopen", Map.of("reason", "test cleanup"));
    }

    JsonNode day(ApiClient who, int year, int month, String date) throws Exception {
        JsonNode days = who.get("/api/v1/time/month/" + year + "/" + month).expect(200).body().get("days");
        return StreamSupport.stream(days.spliterator(), false)
                .filter(d -> d.get("date").asText().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void closedMonthIsFrozenAndRejectsCorrectionsUntilReopened() throws Exception {
        try {
            JsonNode closed = timeAdmin.post("/api/v1/time/closings/2026-08/close", null).expect(200).body();
            assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
            assertThat(closed.get("days").asInt()).isPositive();

            // Frozen values are served, still including the approved absence (Scenario 1 demo data).
            JsonNode aug25 = day(employee, 2026, 8, "2026-08-25");
            assertThat(aug25.get("status").asText()).isEqualTo("CLOSED");
            assertThat(aug25.get("creditedMinutes").asInt()).isEqualTo(480);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM time_account_day WHERE date BETWEEN "
                    + "DATE '2026-08-01' AND DATE '2026-08-31' AND status <> 'CLOSED'", Integer.class)).isZero();

            var correction = employee.post("/api/v1/time/corrections", Map.of("date", "2026-08-25",
                    "operation", "ADD", "requestedTime", "08:00", "requestedType", "CLOCK_IN", "reason", "late"));
            assertThat(correction.status()).isEqualTo(409);
            assertThat(correction.errorCode()).isEqualTo("TIME_MONTH_CLOSED");

            assertThat(timeAdmin.post("/api/v1/time/closings/2026-08/close", null).errorCode())
                    .isEqualTo("TIME_MONTH_CLOSED");
            assertThat(timeAdmin.post("/api/v1/time/closings/2026-08/reopen", Map.of()).errorCode())
                    .isEqualTo("VALIDATION_FAILED");

            timeAdmin.post("/api/v1/time/closings/2026-08/reopen", Map.of("reason", "Late sick note")).expect(200);
            assertThat(day(employee, 2026, 8, "2026-08-25").get("status").asText()).isNotEqualTo("CLOSED");
            assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'TimeMonthClosing'",
                    String.class)).contains("TIME_MONTH_CLOSED", "TIME_MONTH_REOPENED");
        } finally {
            reopenIfClosed("2026-08");
        }
    }

    @Test
    void onlyPastMonthsWithoutPendingCorrectionsCanBeClosed() throws Exception {
        var current = timeAdmin.post("/api/v1/time/closings/2026-09/close", null);
        assertThat(current.status()).isEqualTo(422);
        assertThat(current.errorCode()).isEqualTo("TIME_MONTH_NOT_CLOSABLE");

        String id = employee.post("/api/v1/time/corrections", Map.of("date", "2026-07-01", "operation", "ADD",
                "requestedTime", "08:00", "requestedType", "CLOCK_IN", "reason", "Forgot to book")).expect(200)
                .body().get("id").asText();
        try {
            var blocked = timeAdmin.post("/api/v1/time/closings/2026-07/close", null);
            assertThat(blocked.errorCode()).isEqualTo("TIME_MONTH_NOT_CLOSABLE");
            assertThat(blocked.body().get("message").asText()).contains("still waiting");

            JsonNode overview = timeAdmin.get("/api/v1/time/closings").expect(200).body();
            JsonNode july = StreamSupport.stream(overview.spliterator(), false)
                    .filter(m -> m.get("month").asText().equals("2026-07")).findFirst().orElseThrow();
            assertThat(july.get("pendingCorrections").asInt()).isEqualTo(1);
            assertThat(july.get("closable").asBoolean()).isFalse();

            ApiClient.login(mvc, json, "supervisor").post("/api/v1/time/corrections/" + id + "/reject",
                    Map.of("comment", "Not needed")).expect(200);
            timeAdmin.post("/api/v1/time/closings/2026-07/close", null).expect(200);
        } finally {
            reopenIfClosed("2026-07");
        }
    }

    @Test
    void schedulerClosesThePreviousMonthIdempotently() throws Exception {
        ApiClient admin = ApiClient.login(mvc, json, "erpadmin");
        try {
            assertThat(admin.post("/api/v1/admin/jobs/time-month-closing/run", null).expect(200).body()
                    .get("status").asText()).isEqualTo("SUCCESS");
            assertThat(admin.post("/api/v1/admin/jobs/time-month-closing/run", null).expect(200).body()
                    .get("status").asText()).isEqualTo("SUCCESS");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM time_month_closing WHERE year_month = '2026-08' "
                    + "AND status = 'CLOSED'", Integer.class)).isEqualTo(1);
        } finally {
            reopenIfClosed("2026-08");
        }
    }

    @Test
    void onlyTimeAndHrAdminsManageClosings() throws Exception {
        assertThat(employee.get("/api/v1/time/closings").status()).isEqualTo(403);
        assertThat(employee.post("/api/v1/time/closings/2026-06/close", null).status()).isEqualTo(403);
        assertThat(ApiClient.login(mvc, json, "hradmin").get("/api/v1/time/closings").status()).isEqualTo(200);
    }
}
