package edu.university.ops.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import edu.university.ops.support.TestClockConfiguration.MutableClock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Time acceptance criteria (AGENT.md §71), Demo Scenarios 5 and 6. Test "now": Mon 2026-09-21 08:00. */
@IntegrationTest
class TimeScenarioTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Demo Scenario 5. 'nhalbtag' works 4 h per day and has no demo bookings. */
    @Test
    void clockInBreakAndClockOutProduceTheDailyBalance() throws Exception {
        ApiClient nina = ApiClient.login(mvc, json, "nhalbtag");

        var invalid = nina.post("/api/v1/time/clock-out", null);
        assertThat(invalid.status()).isEqualTo(409);
        assertThat(invalid.errorCode()).isEqualTo("TIME_SEQUENCE_INVALID");

        JsonNode in = nina.post("/api/v1/time/clock-in", null).expect(200).body();
        assertThat(in.get("state").asText()).isEqualTo("WORKING");
        assertThat(in.findValuesAsText("allowedActions").toString()).isNotNull();
        clock.advance(Duration.ofMinutes(120));
        nina.post("/api/v1/time/break-start", null).expect(200);
        clock.advance(Duration.ofMinutes(30));
        nina.post("/api/v1/time/break-end", null).expect(200);
        clock.advance(Duration.ofMinutes(150));
        JsonNode out = nina.post("/api/v1/time/clock-out", null).expect(200).body();

        assertThat(out.get("state").asText()).isEqualTo("OFF");
        assertThat(out.get("allowedActions").get(0).asText()).isEqualTo("CLOCK_IN");
        assertThat(out.at("/account/workedMinutes").asInt()).isEqualTo(270);
        assertThat(out.at("/account/breakMinutes").asInt()).isEqualTo(30);
        assertThat(out.at("/account/targetMinutes").asInt()).isEqualTo(240);
        assertThat(out.at("/account/balanceMinutes").asInt()).isEqualTo(30);

        JsonNode month = nina.get("/api/v1/time/month/2026/9").expect(200).body();
        JsonNode today = StreamSupport.stream(month.get("days").spliterator(), false)
                .filter(d -> d.get("date").asText().equals("2026-09-21")).findFirst().orElseThrow();
        assertThat(today.get("workedMinutes").asInt()).isEqualTo(270);
        assertThat(jdbc.queryForObject("SELECT worked_minutes FROM time_account_day d JOIN employee e "
                + "ON e.id = d.employee_id WHERE e.username = 'nhalbtag' AND d.date = DATE '2026-09-21'",
                Integer.class)).isEqualTo(270);
    }

    /** Demo Scenario 6: a missing clock-out is corrected with supervisor approval. */
    @Test
    void missingClockOutIsCorrectedAfterApproval() throws Exception {
        ApiClient employee = ApiClient.login(mvc, json, "employee");
        ApiClient supervisor = ApiClient.login(mvc, json, "supervisor");

        JsonNode month = employee.get("/api/v1/time/month/2026/9").expect(200).body();
        JsonNode gap = StreamSupport.stream(month.get("days").spliterator(), false)
                .filter(d -> d.get("incomplete").asBoolean()).findFirst().orElseThrow();
        String date = gap.get("date").asText();
        assertThat(gap.get("workedMinutes").asInt()).isZero();

        // An invalid correction is refused up front.
        var invalid = employee.post("/api/v1/time/corrections", Map.of("date", date, "operation", "ADD",
                "requestedTime", "06:00", "requestedType", "CLOCK_OUT", "reason", "test"));
        assertThat(invalid.errorCode()).isEqualTo("TIME_SEQUENCE_INVALID");

        JsonNode correction = employee.post("/api/v1/time/corrections", Map.of("date", date, "operation", "ADD",
                "requestedTime", "17:00", "requestedType", "CLOCK_OUT", "reason", "Forgot to clock out"))
                .expect(200).body();
        String id = correction.get("id").asText();
        assertThat(correction.get("status").asText()).isEqualTo("IN_APPROVAL");

        JsonNode approved = supervisor.post("/api/v1/time/corrections/" + id + "/approve", null).expect(200).body();
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        JsonNode entries = employee.get("/api/v1/time/entries?date=" + date).expect(200).body();
        assertThat(entries.findValuesAsText("type")).contains("CLOCK_OUT");
        assertThat(entries.findValuesAsText("source")).contains("ADMIN");

        JsonNode after = StreamSupport.stream(employee.get("/api/v1/time/month/2026/9").body().get("days")
                .spliterator(), false).filter(d -> d.get("date").asText().equals(date)).findFirst().orElseThrow();
        assertThat(after.get("incomplete").asBoolean()).isFalse();
        assertThat(after.get("workedMinutes").asInt()).isPositive();
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?", String.class, id))
                .contains("TIME_CORRECTION_REQUESTED", "TIME_CORRECTION_APPLIED");
    }

    /** Approved absence affects the time account (AGENT.md §15.4, §69). */
    @Test
    void approvedAbsenceIsCreditedInTheMonthOverview() throws Exception {
        ApiClient employee = ApiClient.login(mvc, json, "employee");
        // Demo data: approved annual leave Mon 2026-08-24 .. Wed 2026-08-26.
        JsonNode month = employee.get("/api/v1/time/month/2026/8").expect(200).body();
        JsonNode day = StreamSupport.stream(month.get("days").spliterator(), false)
                .filter(d -> d.get("date").asText().equals("2026-08-25")).findFirst().orElseThrow();
        assertThat(day.get("absenceType").asText()).isEqualTo("ANNUAL_LEAVE");
        assertThat(day.get("creditedMinutes").asInt()).isEqualTo(480);
        assertThat(day.get("balanceMinutes").asInt()).isZero();
    }
}
