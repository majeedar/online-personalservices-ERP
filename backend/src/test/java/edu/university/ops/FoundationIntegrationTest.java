package edu.university.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.university.ops.support.IntegrationTest;
import edu.university.ops.support.PostgresTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 1/2 acceptance (AGENT.md §68): Flyway + seed data, demo login, the /me
 * endpoints, error format, and the first object-level authorization rules (§66).
 */
@IntegrationTest
class FoundationIntegrationTest extends PostgresTestSupport {

    static final String EMPLOYEE_ID = "20000000-0000-0000-0000-000000000001";
    static final String SUPERVISOR_ID = "20000000-0000-0000-0000-000000000002";
    static final String NHALBTAG_ID = "20000000-0000-0000-0000-000000000011";
    static final String OTTO_ID = "20000000-0000-0000-0000-000000000018";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    @Nested
    class SeedData {

        @Test
        void seedMeetsDemoMinimums() {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM employee", Integer.class)).isGreaterThanOrEqualTo(15);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organisation_unit", Integer.class))
                    .isGreaterThanOrEqualTo(3);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM employment WHERE full_time_equivalent < 1 AND status = 'ACTIVE'",
                    Integer.class)).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    class Authentication {

        @Test
        void loginReturnsServerResolvedRoles() throws Exception {
            mvc.perform(post("/api/v1/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"supervisor\",\"password\":\"demo123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Stefan Beispiel"))
                    .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder("EMPLOYEE", "SUPERVISOR")));
        }

        @Test
        void wrongPasswordIsRejectedAndAudited() throws Exception {
            mvc.perform(post("/api/v1/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"employee\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND actor_username = 'employee'",
                    Integer.class)).isPositive();
        }

        @Test
        void loginWithoutCsrfTokenIsRejected() throws Exception {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"employee\",\"password\":\"demo123\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        }

        @Test
        void unauthenticatedRequestGetsJsonErrorWithCorrelationId() throws Exception {
            mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "test-corr-1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-Correlation-Id", "test-corr-1"))
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                    .andExpect(jsonPath("$.correlationId").value("test-corr-1"));
        }

        @Test
        void maliciousCorrelationIdIsReplaced() throws Exception {
            mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "bad\nvalue"))
                    .andExpect(header().string("X-Correlation-Id", Matchers.not("bad\nvalue")));
        }
    }

    @Nested
    class Me {

        @Test
        void meShowsEmployeeAndOrganisationUnit() throws Exception {
            mvc.perform(get("/api/v1/me").session(login("employee")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.personnelNumber").value("P10001"))
                    .andExpect(jsonPath("$.organisationUnit.code").value("INST-A2"))
                    .andExpect(jsonPath("$.roles", Matchers.contains("EMPLOYEE")));
        }

        @Test
        void partTimeScheduleHasFridayOff() throws Exception {
            mvc.perform(get("/api/v1/me/work-schedule").session(login("parttime")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weeklyTargetMinutes").value(1920))
                    .andExpect(jsonPath("$.days[?(@.weekday=='FRIDAY')].workingDay").value(false))
                    .andExpect(jsonPath("$.days[?(@.weekday=='THURSDAY')].targetMinutes").value(480));
        }

        @Test
        void employmentsMarkTheCurrentContract() throws Exception {
            mvc.perform(get("/api/v1/me/employments").session(login("parttime")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].current").value(true))
                    .andExpect(jsonPath("$[0].fullTimeEquivalent").value(0.8));
        }
    }

    @Nested
    class ObjectLevelAuthorization {

        @Test
        void employeeCannotReadAnotherEmployee() throws Exception {
            mvc.perform(get("/api/v1/employees/" + SUPERVISOR_ID).session(login("employee")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
        }

        @Test
        void supervisorCanReadAssignedEmployee() throws Exception {
            mvc.perform(get("/api/v1/employees/" + EMPLOYEE_ID).session(login("supervisor")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Erika Mustermann"));
        }

        @Test
        void supervisorCannotReadUnassignedEmployee() throws Exception {
            mvc.perform(get("/api/v1/employees/" + OTTO_ID).session(login("supervisor")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void hrAdminCanReadAnyEmployee() throws Exception {
            mvc.perform(get("/api/v1/employees/" + NHALBTAG_ID).session(login("hradmin")))
                    .andExpect(status().isOk());
        }

        @Test
        void erpAdminGetsNoPersonnelDataByDefault() throws Exception {
            mvc.perform(get("/api/v1/employees/" + EMPLOYEE_ID).session(login("erpadmin")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void employeeCannotAccessAdminEndpoints() throws Exception {
            mvc.perform(get("/api/v1/admin/audit").session(login("employee")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Audit {

        @Test
        void auditLogIsAppendOnlyInTheDatabase() throws Exception {
            login("employee");
            assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'TAMPERED'"))
                    .rootCause().hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                    .rootCause().hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
                    .rootCause().hasMessageContaining("append-only");
        }
    }
}
