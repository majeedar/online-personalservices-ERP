package edu.university.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.support.ApiClient;
import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Server texts in the reader's language (ADR-020): per request, stored as templates, test language. */
@IntegrationTest
class LocalizedTextsTest extends PostgresTestSupport {

    static final String ANNUAL = "00000000-0000-0000-0000-000000000301";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    JsonNode notification(ApiClient who, String businessObjectId) throws Exception {
        return StreamSupport.stream(who.get("/api/v1/notifications").expect(200).body().spliterator(), false)
                .filter(n -> businessObjectId.equals(n.path("businessObjectId").asText())).findFirst().orElseThrow();
    }

    @Test
    void storedNotificationsAndTasksAreShownInTheReadersLanguage() throws Exception {
        ApiClient employee = ApiClient.login(mvc, json, "employee");
        String id = employee.post("/api/v1/absences", Map.of("leaveTypeId", ANNUAL, "startDate", "2027-11-08",
                "endDate", "2027-11-09")).expect(200).body().get("id").asText();
        employee.post("/api/v1/absences/" + id + "/submit", null).expect(200);

        // One stored notification, read in three languages.
        assertThat(notification(employee, id).get("message").asText())
                .isEqualTo("Your annual leave request for 08.11.2027 – 09.11.2027 was submitted for approval.");
        JsonNode german = notification(employee.withLanguage("de-DE,de;q=0.9"), id);
        assertThat(german.get("subject").asText()).isEqualTo("Abwesenheitsantrag eingereicht");
        assertThat(german.get("message").asText())
                .isEqualTo("Ihr Antrag auf Erholungsurlaub für 08.11.2027 – 09.11.2027 wurde zur Genehmigung eingereicht.");
        assertThat(notification(employee.withLanguage("qps-ploc"), id).get("subject").asText()).startsWith("⟦");

        // The approver's task title, and the leave type in the German API.
        ApiClient supervisor = ApiClient.login(mvc, json, "supervisor").withLanguage("de");
        assertThat(supervisor.get("/api/v1/tasks").expect(200).body().findValuesAsText("title"))
                .contains("Erholungsurlaub genehmigen – Erika Mustermann");
        assertThat(employee.withLanguage("de").get("/api/v1/absences/" + id).body().at("/leaveType/name").asText())
                .isEqualTo("Erholungsurlaub");
    }

    @Test
    void errorsFollowTheRequestLanguage() throws Exception {
        ApiClient employee = ApiClient.login(mvc, json, "employee");
        var english = employee.post("/api/v1/absences", Map.of("leaveTypeId", ANNUAL, "startDate", "2027-11-19",
                "endDate", "2027-11-15"));
        assertThat(english.body().get("message").asText()).isEqualTo("The end date must not be before the start date.");
        var german = employee.withLanguage("de").post("/api/v1/absences", Map.of("leaveTypeId", ANNUAL,
                "startDate", "2027-11-19", "endDate", "2027-11-15"));
        assertThat(german.body().get("message").asText()).isEqualTo("Das Enddatum darf nicht vor dem Startdatum liegen.");

        // Errors from the security filter chain, before Spring MVC sets the locale.
        String unauthenticated = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me").header("Accept-Language", "de"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(unauthenticated).contains("Bitte melden Sie sich an.");
    }

    @Test
    void theChosenLanguageIsSavedForTheSessionAndEmails() throws Exception {
        ApiClient hr = ApiClient.login(mvc, json, "hradmin");
        try {
            hr.put("/api/v1/me/language", Map.of("language", "de")).expect(204);
            assertThat(hr.get("/api/v1/auth/session").body().get("language").asText()).isEqualTo("de");
            assertThat(hr.put("/api/v1/me/language", Map.of("language", "fr")).errorCode())
                    .isEqualTo("VALIDATION_FAILED");
        } finally {
            hr.put("/api/v1/me/language", Map.of("language", "en")).expect(204);
        }
    }
}
