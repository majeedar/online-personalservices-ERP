package edu.university.ops.organisation.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.organisation.domain.OrganisationGateway;
import edu.university.ops.organisation.domain.OrganisationGateway.ExternalOrganisationUnit;
import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.HttpErrors;
import edu.university.ops.shared.integration.SimulatedOutages;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

/** Adapters for the personnel ERP's organisation interface (stub and HTTP). */
@Configuration
class OrganisationAdapters {

    /** ERP wire format (AGENT.md §24): external names stay in the adapter. */
    record OrgUnitDto(@JsonProperty("ORG_CODE") String orgCode, @JsonProperty("ORG_ID") String orgId,
                      @JsonProperty("ORG_NAME") String orgName, @JsonProperty("ORG_TYPE") String orgType,
                      @JsonProperty("PARENT_ORG") String parentOrg, @JsonProperty("KOSTL") String kostl,
                      @JsonProperty("ACTIVE") boolean active, @JsonProperty("CHANGED_AT") Instant changedAt) {
        ExternalOrganisationUnit toExternal() {
            return new ExternalOrganisationUnit(orgCode, orgId, orgName, orgType, parentOrg, kostl, active, changedAt);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "ops.integration.mode", havingValue = "stub", matchIfMissing = true)
    OrganisationGateway stubOrganisationGateway(ObjectMapper json, SimulatedOutages outages) {
        List<OrgUnitDto> data = read(json);
        return since -> {
            outages.check(ExternalSystem.PERSONNEL_ERP);
            return data.stream().filter(d -> d.changedAt().isAfter(since)).map(OrgUnitDto::toExternal).toList();
        };
    }

    @Bean
    @ConditionalOnProperty(name = "ops.integration.mode", havingValue = "http")
    OrganisationGateway httpOrganisationGateway(RestClient mockErpClient) {
        return since -> {
            List<OrgUnitDto> dtos = HttpErrors.call(ExternalSystem.PERSONNEL_ERP, () -> mockErpClient.get()
                    .uri("/mock/personnel/organisation-units?since={since}", since.toString())
                    .retrieve().body(new ParameterizedTypeReference<List<OrgUnitDto>>() { }));
            return dtos == null ? List.of() : dtos.stream().map(OrgUnitDto::toExternal).toList();
        };
    }

    private static List<OrgUnitDto> read(ObjectMapper json) {
        try (InputStream in = new ClassPathResource("mock-data/org-unit-changes.json").getInputStream()) {
            return json.readValue(in, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
