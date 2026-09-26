package edu.university.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import edu.university.ops.support.TestClockConfiguration;
import edu.university.ops.support.TestClockConfiguration.MutableClock;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Data retention (AGENT.md §82): personal free text and attachments go, structural data stays. */
@IntegrationTest
class DataRetentionTest extends PostgresTestSupport {

    static final String ANNUAL = "00000000-0000-0000-0000-000000000301";
    static final String SICK = "00000000-0000-0000-0000-000000000303";
    static final String CLARA = "20000000-0000-0000-0000-000000000012";

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

    @Test
    void finishedRequestsAreAnonymisedAfterTheRetentionPeriod() throws Exception {
        ApiClient employee = ApiClient.login(mvc, json, "employee");
        ApiClient supervisor = ApiClient.login(mvc, json, "supervisor");

        // An old request with every kind of personal detail ...
        String old = employee.post("/api/v1/absences", Map.of("leaveTypeId", ANNUAL, "startDate", "2027-02-22",
                "endDate", "2027-02-23", "representativeId", CLARA, "comment", "Hospital visit")).expect(200).body()
                .get("id").asText();
        employee.perform(MockMvcRequestBuilders.multipart("/api/v1/absences/" + old + "/documents")
                .file(new MockMultipartFile("file", "note.pdf", "application/pdf", "%PDF-1.4 note".getBytes()))
                .with(csrf())).expect(200);
        employee.post("/api/v1/absences/" + old + "/submit", null).expect(200);
        supervisor.post("/api/v1/absences/" + old + "/approve", Map.of("comment", "Get well soon")).expect(200);
        // ... and a recent one, still within the retention period.
        String recent = employee.post("/api/v1/absences", Map.of("leaveTypeId", SICK, "startDate", "2029-12-03",
                "endDate", "2029-12-03", "comment", "recent")).expect(200).body().get("id").asText();
        employee.post("/api/v1/absences/" + recent + "/submit", null).expect(200);

        // Three and a half years later (absence retention: 1095 days after the end).
        clock.set(ZonedDateTime.of(2030, 6, 1, 3, 0, 0, 0, TestClockConfiguration.ZONE));
        ApiClient admin = ApiClient.login(mvc, json, "erpadmin");
        JsonNode run = admin.post("/api/v1/admin/jobs/data-retention/run", null).expect(200).body();
        assertThat(run.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(run.get("processedRecords").asInt()).isPositive();

        JsonNode anonymised = employee.get("/api/v1/absences/" + old).expect(200).body();
        assertThat(anonymised.path("comment").isMissingNode() || anonymised.get("comment").isNull()).isTrue();
        assertThat(anonymised.path("representative").isMissingNode()
                || anonymised.get("representative").isNull()).isTrue();
        assertThat(anonymised.get("documents")).isEmpty();
        assertThat(anonymised.get("anonymisedAt").asText()).startsWith("2030-06-01");
        assertThat(anonymised.findValuesAsText("comment")).isEmpty();
        // Structural data stays: status, days and the decision itself.
        assertThat(anonymised.get("status").asText()).isEqualTo("APPROVED");
        assertThat(anonymised.get("workingDays").asInt()).isEqualTo(2);
        assertThat(anonymised.findValuesAsText("decision")).containsExactly("APPROVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document WHERE business_object_id = ?::uuid",
                Integer.class, old)).isZero();

        assertThat(employee.get("/api/v1/absences/" + recent).body().get("comment").asText()).isEqualTo("recent");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE created_at < TIMESTAMPTZ "
                + "'2029-12-03 00:00:00+01'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'DataRetention'",
                String.class)).contains("DATA_RETENTION_APPLIED");

        // Idempotent: nothing left to do on the next run.
        JsonNode again = admin.post("/api/v1/admin/jobs/data-retention/run", null).expect(200).body();
        assertThat(again.get("processedRecords").asInt()).isZero();
    }
}
