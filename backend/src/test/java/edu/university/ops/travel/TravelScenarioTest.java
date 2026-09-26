package edu.university.ops.travel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.shared.integration.IntegrationRun;
import edu.university.ops.shared.integration.OutboxProcessor;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Travel acceptance criteria (AGENT.md §70) and Demo Scenarios 3, 4 and 7. Test "now": 2026-09-21. */
@IntegrationTest
class TravelScenarioTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxProcessor outbox;

    ApiClient employee;
    ApiClient supervisor;
    ApiClient finance;
    ApiClient travelOffice;

    @BeforeEach
    void login() throws Exception {
        employee = ApiClient.login(mvc, json, "employee");
        supervisor = ApiClient.login(mvc, json, "supervisor");
        finance = ApiClient.login(mvc, json, "finance");
        travelOffice = ApiClient.login(mvc, json, "travel");
    }

    Map<String, Object> trip(String start, String end, String costCentre) {
        Map<String, Object> body = new HashMap<>();
        body.put("purpose", "Research visit");
        body.put("destinationCity", "Berlin");
        body.put("destinationCountry", "DE");
        body.put("startDateTime", start);
        body.put("endDateTime", end);
        body.put("transportMode", "TRAIN");
        body.put("estimatedCost", 500);
        body.put("currency", "EUR");
        body.put("costCentre", costCentre);
        return body;
    }

    String fundingSource(String costCentre, String project) throws Exception {
        JsonNode sources = employee.get("/api/v1/funding-sources").expect(200).body();
        return StreamSupport.stream(sources.spliterator(), false)
                .filter(s -> s.get("costCentre").asText().equals(costCentre)
                        && (project == null ? s.path("projectCode").isMissingNode() || s.get("projectCode").isNull()
                        : project.equals(s.path("projectCode").asText())))
                .findFirst().orElseThrow().get("id").asText();
    }

    /** An authorized trip that already took place (yesterday), ready for expenses. */
    String completedTrip() throws Exception {
        String id = employee.post("/api/v1/travel", trip("2026-09-19T08:00:00+02:00", "2026-09-19T20:00:00+02:00",
                "CC-2200")).expect(200).body().get("id").asText();
        employee.post("/api/v1/travel/" + id + "/submit", null).expect(200);
        supervisor.post("/api/v1/travel/" + id + "/approve", null).expect(200);
        finance.post("/api/v1/travel/" + id + "/financial-approve", null).expect(200);
        outbox.processDue(IntegrationRun.Trigger.EVENT);
        employee.post("/api/v1/travel/" + id + "/mark-completed", null).expect(200);
        return id;
    }

    /** Demo Scenario 3. */
    @Test
    void tripIsApprovedTwiceAuthorizedAndExported() throws Exception {
        Map<String, Object> body = trip("2026-10-12T08:00:00+02:00", "2026-10-13T19:00:00+02:00", "CC-2200");
        body.put("fundings", List.of(
                Map.of("fundingSourceId", fundingSource("CC-2200", null), "percentage", 60),
                Map.of("fundingSourceId", fundingSource("CC-2201", "PRJ-ORD-2026"), "percentage", 40)));
        String id = employee.post("/api/v1/travel", body).expect(200).body().get("id").asText();

        JsonNode submitted = employee.post("/api/v1/travel/" + id + "/submit", null).expect(200).body();
        assertThat(submitted.get("status").asText()).isEqualTo("IN_APPROVAL");

        // Financial approval is not possible before the supervisor has approved.
        assertThat(finance.post("/api/v1/travel/" + id + "/financial-approve", null).status()).isEqualTo(403);
        JsonNode afterSupervisor = supervisor.post("/api/v1/travel/" + id + "/approve", null).expect(200).body();
        assertThat(afterSupervisor.get("status").asText()).isEqualTo("IN_APPROVAL");
        // The supervisor cannot also perform the financial approval.
        assertThat(supervisor.post("/api/v1/travel/" + id + "/financial-approve", null).status()).isEqualTo(403);

        JsonNode financeTasks = finance.get("/api/v1/tasks").expect(200).body();
        assertThat(financeTasks.findValuesAsText("businessObjectId")).contains(id);
        JsonNode authorized = finance.post("/api/v1/travel/" + id + "/financial-approve", null).expect(200).body();
        assertThat(authorized.get("status").asText()).isEqualTo("AUTHORIZED");

        outbox.processDue(IntegrationRun.Trigger.EVENT);
        JsonNode exported = employee.get("/api/v1/travel/" + id).expect(200).body();
        assertThat(exported.get("externalTravelReference").asText()).startsWith("TRV-");
        assertThat(exported.findValuesAsText("decision")).containsExactly("APPROVE", "APPROVE");
        // By business object: the test clock is fixed, so "latest 20" of the list endpoint has no stable order.
        assertThat(jdbc.queryForList("SELECT type FROM notification WHERE business_object_id = ?::uuid", String.class,
                id)).contains("TRAVEL_AUTHORIZED");
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?", String.class, id))
                .contains("TRAVEL_SUBMITTED", "TRAVEL_APPROVED");
    }

    @Test
    void invalidCostCentreAndFundingAreRejected() throws Exception {
        String bad = employee.post("/api/v1/travel", trip("2026-10-20T08:00:00+02:00", "2026-10-20T18:00:00+02:00",
                "CC-9999")).expect(200).body().get("id").asText();
        var response = employee.post("/api/v1/travel/" + bad + "/submit", null);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.errorCode()).isEqualTo("COST_CENTRE_INVALID");

        Map<String, Object> body = trip("2026-10-21T08:00:00+02:00", "2026-10-21T18:00:00+02:00", "CC-2200");
        body.put("fundings", List.of(Map.of("fundingSourceId", fundingSource("CC-2200", null), "percentage", 50),
                Map.of("fundingSourceId", fundingSource("CC-2201", "PRJ-ORD-2026"), "percentage", 30)));
        String split = employee.post("/api/v1/travel", body).expect(200).body().get("id").asText();
        assertThat(employee.post("/api/v1/travel/" + split + "/submit", null).errorCode()).isEqualTo("FUNDING_INVALID");

        // Other employees cannot see the trip.
        assertThat(ApiClient.login(mvc, json, "parttime").get("/api/v1/travel/" + split).status()).isEqualTo(403);
    }

    /** Demo Scenario 4. */
    @Test
    void expensesAreClaimedReviewedAndSettled() throws Exception {
        String id = completedTrip();
        JsonNode hotel = employee.post("/api/v1/travel/" + id + "/expenses", Map.of("expenseType", "HOTEL",
                "expenseDate", "2026-09-19", "amount", 180, "description", "Hotel")).expect(200).body();
        employee.post("/api/v1/travel/" + id + "/expenses", Map.of("expenseType", "TRAIN",
                "expenseDate", "2026-09-19", "amount", 89.9, "description", "Train")).expect(200);

        assertThat(employee.post("/api/v1/travel/" + id + "/submit-expenses", null).errorCode())
                .isEqualTo("ATTACHMENT_REQUIRED");
        for (JsonNode expense : employee.get("/api/v1/travel/" + id).body().get("expenses")) {
            MockMultipartFile receipt = new MockMultipartFile("file", "receipt.pdf", "application/pdf",
                    "%PDF-1.4 fake receipt".getBytes());
            employee.perform(MockMvcRequestBuilders.multipart("/api/v1/travel/" + id + "/expenses/"
                            + expense.get("id").asText() + "/receipt").file(receipt)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .csrf())).expect(200);
        }
        assertThat(hotel.get("expenseTotal").asDouble()).isEqualTo(180.0);
        employee.post("/api/v1/travel/" + id + "/submit-expenses", null).expect(200);

        JsonNode tasks = travelOffice.get("/api/v1/tasks").expect(200).body();
        assertThat(tasks.findValuesAsText("businessObjectId")).contains(id);
        JsonNode settled = travelOffice.post("/api/v1/travel/" + id + "/settle", null).expect(200).body();
        assertThat(settled.get("status").asText()).isEqualTo("SETTLED");
        assertThat(settled.get("settledAmount").asDouble()).isEqualTo(269.9);

        outbox.processDue(IntegrationRun.Trigger.EVENT);
        JsonNode exported = employee.get("/api/v1/travel/" + id).body();
        assertThat(exported.get("financePostingReference").asText()).startsWith("FIN-");
        assertThat(exported.get("settlementReference").asText()).startsWith("STL-");
    }

    /** Demo Scenario 7: finance outage, recorded error, retry after recovery, no duplicate posting. */
    @Test
    void financeOutageIsRecordedAndRetriedWithoutDuplicates() throws Exception {
        ApiClient admin = ApiClient.login(mvc, json, "erpadmin");
        String id = completedTrip();
        employee.post("/api/v1/travel/" + id + "/expenses", Map.of("expenseType", "MEALS",
                "expenseDate", "2026-09-19", "amount", 24)).expect(200);
        employee.post("/api/v1/travel/" + id + "/submit-expenses", null).expect(200);

        admin.post("/api/v1/admin/external-systems/FINANCE_ERP/outage?down=true", null).expect(200);
        try {
            travelOffice.post("/api/v1/travel/" + id + "/settle", null).expect(200);
            outbox.processDue(IntegrationRun.Trigger.EVENT);

            JsonNode trip = employee.get("/api/v1/travel/" + id).body();
            assertThat(trip.path("financePostingReference").isMissingNode()
                    || trip.get("financePostingReference").isNull()).isTrue();
            JsonNode errors = admin.get("/api/v1/admin/integration-errors").expect(200).body().get("items");
            JsonNode error = StreamSupport.stream(errors.spliterator(), false)
                    .filter(e -> ("finance-posting-" + id).equals(e.get("externalReference").asText()))
                    .findFirst().orElseThrow();
            assertThat(error.get("errorCode").asText()).isEqualTo("CONNECTION_REFUSED");
            assertThat(error.get("retryable").asBoolean()).isTrue();
            assertThat(admin.get("/api/v1/admin/system-health").body().at("/components/Finance ERP").asText())
                    .isEqualTo("DOWN");

            admin.post("/api/v1/admin/external-systems/FINANCE_ERP/outage?down=false", null).expect(200);
            JsonNode retried = admin.post("/api/v1/admin/integration-errors/" + error.get("id").asText() + "/retry",
                    null).expect(200).body();
            assertThat(retried.get("resolved").asBoolean()).isTrue();

            String reference = employee.get("/api/v1/travel/" + id).body().get("financePostingReference").asText();
            assertThat(reference).startsWith("FIN-");
            // Delivered exactly once: further processing does not post again.
            outbox.processDue(IntegrationRun.Trigger.RETRY);
            assertThat(employee.get("/api/v1/travel/" + id).body().get("financePostingReference").asText())
                    .isEqualTo(reference);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE idempotency_key = ?",
                    Integer.class, "finance-posting-" + id)).isEqualTo(1);
        } finally {
            admin.post("/api/v1/admin/external-systems/FINANCE_ERP/outage?down=false", null);
        }
    }
}
