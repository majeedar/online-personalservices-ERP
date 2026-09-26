package edu.university.mockerp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MockErpTest {

    @Autowired
    MockMvc mvc;

    @Test
    void postingsAreIdempotent() throws Exception {
        String first = mvc.perform(post("/mock/finance/postings").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"BETRAG\": 10, \"WAERS\": \"EUR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/mock/finance/postings").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"BETRAG\": 10, \"WAERS\": \"EUR\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString()).isEqualTo(first));
    }

    @Test
    void outageReturns503UntilRestored() throws Exception {
        mvc.perform(post("/mock/admin/outage/FINANCE_ERP?down=true")).andExpect(status().isOk());
        mvc.perform(get("/mock/finance/cost-centres/CC-2200")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/mock/admin/outage/FINANCE_ERP?down=false")).andExpect(status().isOk());
        mvc.perform(get("/mock/finance/cost-centres/CC-2200")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ACTIVE").value(true));
    }

    @Test
    void personnelChangesUseErpFieldNames() throws Exception {
        mvc.perform(get("/mock/personnel/changes?since=2026-09-03T00:00:00Z")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].PERS_NR").exists())
                .andExpect(jsonPath("$[0].SUPERVISOR_REF").exists());
    }
}
