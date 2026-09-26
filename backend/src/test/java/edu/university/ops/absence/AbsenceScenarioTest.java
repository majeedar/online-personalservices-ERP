package edu.university.ops.absence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Absence acceptance criteria (AGENT.md §69) and Demo Scenarios 1 and 2.
 * Each test uses its own date range so tests stay independent on the shared DB.
 */
@IntegrationTest
class AbsenceScenarioTest extends PostgresTestSupport {

    static final String ANNUAL = "00000000-0000-0000-0000-000000000301";
    static final String SICK = "00000000-0000-0000-0000-000000000303";
    static final String FLEX = "00000000-0000-0000-0000-000000000302";
    static final String EMPLOYEE_ID = "20000000-0000-0000-0000-000000000001";
    static final String SUPERVISOR_ID = "20000000-0000-0000-0000-000000000002";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AbsenceLookup absences;

    ApiClient employee;
    ApiClient supervisor;

    @BeforeEach
    void login() throws Exception {
        employee = ApiClient.login(mvc, json, "employee");
        supervisor = ApiClient.login(mvc, json, "supervisor");
    }

    static Map<String, Object> body(String leaveType, String from, String to) {
        return Map.of("leaveTypeId", leaveType, "startDate", from, "endDate", to);
    }

    String submitted(ApiClient who, String leaveType, String from, String to) throws Exception {
        String id = who.post("/api/v1/absences", body(leaveType, from, to)).expect(200).body().get("id").asText();
        who.post("/api/v1/absences/" + id + "/submit", null).expect(200);
        return id;
    }

    JsonNode annualBalance(ApiClient who, int year) throws Exception {
        JsonNode balances = who.get("/api/v1/leave-balances?year=" + year).expect(200).body();
        return StreamSupport.stream(balances.spliterator(), false)
                .filter(b -> b.get("leaveTypeCode").asText().equals("ANNUAL_LEAVE")).findFirst().orElseThrow();
    }

    /** Demo Scenario 1. */
    @Test
    void annualLeaveIsSubmittedApprovedAndBooked() throws Exception {
        double before = annualBalance(employee, 2027).get("remainingDays").asDouble();
        double reservedBefore = annualBalance(employee, 2027).get("reservedDays").asDouble();

        // Mon 2027-02-15 .. Fri 2027-02-19: five working days
        String id = employee.post("/api/v1/absences", body(ANNUAL, "2027-02-15", "2027-02-19")).expect(200).body()
                .get("id").asText();
        JsonNode submitted = employee.post("/api/v1/absences/" + id + "/submit", null).expect(200).body();
        assertThat(submitted.get("status").asText()).isEqualTo("IN_APPROVAL");
        assertThat(submitted.get("workingDays").asInt()).isEqualTo(5);
        assertThat(annualBalance(employee, 2027).get("reservedDays").asDouble()).isEqualTo(reservedBefore + 5);

        // The supervisor sees the task and approves.
        JsonNode tasks = supervisor.get("/api/v1/tasks").expect(200).body();
        assertThat(tasks.findValuesAsText("businessObjectId")).contains(id);
        JsonNode approved = supervisor.post("/api/v1/absences/" + id + "/approve", Map.of("comment", "Enjoy"))
                .expect(200).body();
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        // Balance updated, notification and history present.
        JsonNode balance = annualBalance(employee, 2027);
        assertThat(balance.get("remainingDays").asDouble()).isEqualTo(before - 5);
        assertThat(balance.get("reservedDays").asDouble()).isEqualTo(reservedBefore);
        JsonNode notes = employee.get("/api/v1/notifications").expect(200).body();
        assertThat(notes.findValuesAsText("type")).contains("ABSENCE_APPROVED");
        JsonNode detail = employee.get("/api/v1/absences/" + id).expect(200).body();
        JsonNode decision = detail.at("/history/0/steps/0/decisions/0");
        assertThat(decision.get("approverName").asText()).isEqualTo("Stefan Beispiel");
        assertThat(decision.get("decision").asText()).isEqualTo("APPROVE");

        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?", String.class, id))
                .contains("ABSENCE_SUBMITTED", "ABSENCE_APPROVED");
    }

    /** Demo Scenario 2: only actual working days of the part-time schedule count. */
    @Test
    void partTimeLeaveCountsOnlyScheduledWorkingDays() throws Exception {
        ApiClient partTime = ApiClient.login(mvc, json, "parttime");
        // Thu 2027-03-04 .. Mon 2027-03-08 -> Thu + Mon (Friday is not a working day)
        JsonNode preview = partTime.post("/api/v1/absences/preview", body(ANNUAL, "2027-03-04", "2027-03-08"))
                .expect(200).body();
        assertThat(preview.get("workingDays").asInt()).isEqualTo(2);
        assertThat(preview.get("deduction").asDouble()).isEqualTo(2);
        assertThat(preview.get("projectedBalance").asDouble())
                .isEqualTo(preview.get("currentBalance").asDouble() - 2);

        // Thu 2027-03-25 .. Mon 2027-03-29 -> only Thursday (Good Friday, weekend, Easter Monday)
        JsonNode easter = partTime.post("/api/v1/absences/preview", body(ANNUAL, "2027-03-25", "2027-03-29"))
                .expect(200).body();
        assertThat(easter.get("workingDays").asInt()).isEqualTo(1);
        assertThat(easter.findValuesAsText("kind")).contains("HOLIDAY", "NON_WORKING_DAY");
    }

    @Test
    void overlappingRequestsAreRejected() throws Exception {
        submitted(employee, ANNUAL, "2027-04-12", "2027-04-14");
        String second = employee.post("/api/v1/absences", body(ANNUAL, "2027-04-14", "2027-04-16")).expect(200)
                .body().get("id").asText();
        var response = employee.post("/api/v1/absences/" + second + "/submit", null);
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("ABSENCE_OVERLAP");
    }

    @Test
    void insufficientBalanceIsRejected() throws Exception {
        String id = employee.post("/api/v1/absences", body(ANNUAL, "2027-07-01", "2027-09-30")).expect(200).body()
                .get("id").asText();
        var response = employee.post("/api/v1/absences/" + id + "/submit", null);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.errorCode()).isEqualTo("INSUFFICIENT_LEAVE_BALANCE");
    }

    @Test
    void invalidInputsAreReported() throws Exception {
        assertThat(employee.post("/api/v1/absences", body(ANNUAL, "2027-05-10", "2027-05-07")).errorCode())
                .isEqualTo("INVALID_DATE_RANGE");

        JsonNode weekend = employee.post("/api/v1/absences/preview", body(ANNUAL, "2027-05-08", "2027-05-09"))
                .expect(200).body();
        assertThat(weekend.findValuesAsText("code")).contains("ABSENCE_NO_WORKING_DAYS");

        var ownRepresentative = new java.util.HashMap<>(body(ANNUAL, "2027-05-10", "2027-05-11"));
        ownRepresentative.put("representativeId", "20000000-0000-0000-0000-000000000001");
        JsonNode preview = employee.post("/api/v1/absences/preview", ownRepresentative).expect(200).body();
        assertThat(preview.findValuesAsText("code")).contains("INVALID_REPRESENTATIVE");
    }

    @Test
    void approvalRulesAreEnforced() throws Exception {
        // A supervisor cannot approve their own request (their approver is supervisor2).
        String own = submitted(supervisor, ANNUAL, "2027-05-17", "2027-05-18");
        assertThat(supervisor.post("/api/v1/absences/" + own + "/approve", null).status()).isEqualTo(403);

        String id = submitted(employee, ANNUAL, "2027-05-24", "2027-05-25");
        // Someone without an approval relation cannot decide or even read it.
        ApiClient finance = ApiClient.login(mvc, json, "finance");
        assertThat(finance.post("/api/v1/absences/" + id + "/approve", null).status()).isEqualTo(403);
        assertThat(finance.get("/api/v1/absences/" + id).status()).isEqualTo(403);
        // Rejection requires a reason.
        assertThat(supervisor.post("/api/v1/absences/" + id + "/reject", Map.of()).errorCode())
                .isEqualTo("VALIDATION_FAILED");
        // Double processing: the second approval of the same task fails cleanly.
        supervisor.post("/api/v1/absences/" + id + "/approve", null).expect(200);
        var again = supervisor.post("/api/v1/absences/" + id + "/approve", null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.errorCode()).isEqualTo("INVALID_WORKFLOW_STATE");
    }

    @Test
    void rejectionAndReturnReleaseTheReservation() throws Exception {
        double before = annualBalance(employee, 2027).get("remainingDays").asDouble();
        String id = submitted(employee, ANNUAL, "2027-06-07", "2027-06-08");
        supervisor.post("/api/v1/absences/" + id + "/return", Map.of("comment", "Please add a representative"))
                .expect(200);
        JsonNode returned = employee.get("/api/v1/absences/" + id).expect(200).body();
        assertThat(returned.get("status").asText()).isEqualTo("DRAFT");
        assertThat(returned.at("/actions/submit").asBoolean()).isTrue();

        employee.post("/api/v1/absences/" + id + "/submit", null).expect(200);
        supervisor.post("/api/v1/absences/" + id + "/reject", Map.of("comment", "Team event")).expect(200);
        assertThat(employee.get("/api/v1/absences/" + id).body().get("status").asText()).isEqualTo("REJECTED");
        assertThat(annualBalance(employee, 2027).get("remainingDays").asDouble()).isEqualTo(before);
    }

    @Test
    void approvedAbsenceCancellationNeedsApprovalAndRestoresBalance() throws Exception {
        double before = annualBalance(employee, 2027).get("remainingDays").asDouble();
        String id = submitted(employee, ANNUAL, "2027-06-14", "2027-06-16");
        supervisor.post("/api/v1/absences/" + id + "/approve", null).expect(200);
        assertThat(annualBalance(employee, 2027).get("remainingDays").asDouble()).isEqualTo(before - 3);

        JsonNode requested = employee.post("/api/v1/absences/" + id + "/cancel", null).expect(200).body();
        assertThat(requested.get("status").asText()).isEqualTo("CANCEL_REQUESTED");
        supervisor.post("/api/v1/absences/" + id + "/approve", null).expect(200);

        assertThat(employee.get("/api/v1/absences/" + id).body().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(annualBalance(employee, 2027).get("remainingDays").asDouble()).isEqualTo(before);
    }

    @Test
    void sickLeaveTakesEffectWithoutApproval() throws Exception {
        String id = submitted(employee, SICK, "2027-01-11", "2027-01-12");
        JsonNode detail = employee.get("/api/v1/absences/" + id).expect(200).body();
        assertThat(detail.get("status").asText()).isEqualTo("APPROVED");
        assertThat(detail.get("deduction").asDouble()).isZero();
    }

    @Test
    void delegateCanDecideOnBehalfOfTheSupervisor() throws Exception {
        String delegation = supervisor.post("/api/v1/delegations", Map.of(
                "delegateId", "20000000-0000-0000-0000-000000000010", "approvalType", "ABSENCE",
                "validFrom", "2026-09-21", "validTo", "2026-09-30")).expect(200).body().get("id").asText();
        try {
            String id = submitted(employee, ANNUAL, "2027-06-21", "2027-06-22");
            ApiClient deputy = ApiClient.login(mvc, json, "supervisor2");
            JsonNode tasks = deputy.get("/api/v1/tasks").expect(200).body();
            JsonNode task = StreamSupport.stream(tasks.spliterator(), false)
                    .filter(t -> t.get("businessObjectId").asText().equals(id)).findFirst().orElseThrow();
            assertThat(task.get("viaDelegationFrom").asText()).isEqualTo(SUPERVISOR_ID);

            deputy.post("/api/v1/tasks/" + task.get("id").asText() + "/complete", Map.of("decision", "APPROVE"))
                    .expect(200);
            JsonNode decision = employee.get("/api/v1/absences/" + id).body().at("/history/0/steps/0/decisions/0");
            assertThat(decision.get("approverName").asText()).isEqualTo("Bettina Leitung");
            assertThat(decision.get("onBehalfOfName").asText()).isEqualTo("Stefan Beispiel");
        } finally {
            supervisor.post("/api/v1/delegations/" + delegation + "/revoke", null).expect(204);
        }
    }

    /** ADR-018: a morning and an afternoon absence share a date; a full day on it overlaps. */
    @Test
    void halfDaysDeductHalfAndMayShareADate() throws Exception {
        double reservedBefore = annualBalance(employee, 2027).get("reservedDays").asDouble();
        var morning = new HashMap<String, Object>(body(ANNUAL, "2027-10-04", "2027-10-04"));
        morning.put("startDayPart", "MORNING");
        String annual = employee.post("/api/v1/absences", morning).expect(200).body().get("id").asText();
        JsonNode submitted = employee.post("/api/v1/absences/" + annual + "/submit", null).expect(200).body();
        assertThat(submitted.get("workingDays").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(submitted.get("deduction").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(submitted.at("/days/0/dayPart").asText()).isEqualTo("MORNING");
        assertThat(annualBalance(employee, 2027).get("reservedDays").asDouble()).isEqualTo(reservedBefore + 0.5);

        // The afternoon is still free for a flex day ...
        var afternoon = new HashMap<String, Object>(body(FLEX, "2027-10-04", "2027-10-04"));
        afternoon.put("endDayPart", "AFTERNOON");
        String flex = employee.post("/api/v1/absences", afternoon).expect(200).body().get("id").asText();
        employee.post("/api/v1/absences/" + flex + "/submit", null).expect(200);
        // ... but not for a full day.
        String full = employee.post("/api/v1/absences", body(ANNUAL, "2027-10-04", "2027-10-05")).expect(200)
                .body().get("id").asText();
        assertThat(employee.post("/api/v1/absences/" + full + "/submit", null).errorCode())
                .isEqualTo("ABSENCE_OVERLAP");

        supervisor.post("/api/v1/absences/" + annual + "/approve", Map.of()).expect(200);
        supervisor.post("/api/v1/absences/" + flex + "/approve", Map.of()).expect(200);
        var day = absences.approvedAbsences(UUID.fromString(EMPLOYEE_ID), LocalDate.of(2027, 10, 4),
                LocalDate.of(2027, 10, 4)).values().iterator().next();
        int target = submitted.at("/days/0/plannedMinutes").asInt() * 2;
        assertThat(day.plannedMinutes()).isBetween(target - 1, target);
        assertThat(day.creditedMinutes()).isEqualTo(submitted.at("/days/0/creditedMinutes").asInt());
        assertThat(day.leaveTypeCode()).contains("ANNUAL_LEAVE", "FLEX_DAY");

        var invalid = new HashMap<String, Object>(body(ANNUAL, "2027-10-11", "2027-10-12"));
        invalid.put("startDayPart", "MORNING");
        assertThat(employee.post("/api/v1/absences", invalid).errorCode()).isEqualTo("INVALID_DAY_PART");
    }

    @Test
    void teamViewHidesTheLeaveType() throws Exception {
        JsonNode team = supervisor.get("/api/v1/team/absences?from=2026-09-01&to=2026-12-31").expect(200).body();
        assertThat(team.size()).isPositive();
        assertThat(team.findValues("leaveType")).isEmpty();
    }
}
