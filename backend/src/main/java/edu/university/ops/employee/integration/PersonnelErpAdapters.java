package edu.university.ops.employee.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.employee.domain.EmployeeMasterDataGateway;
import edu.university.ops.employee.domain.EmployeeMasterDataGateway.ExternalEmployee;
import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.HttpErrors;
import edu.university.ops.shared.integration.SimulatedOutages;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Adapters for the personnel ERP employee interface (stub and HTTP). */
@Configuration
class PersonnelErpAdapters {

    /** ERP wire format (AGENT.md §24); external field names never leave this adapter. */
    record PersonnelDto(@JsonProperty("PERS_NR") String persNr, @JsonProperty("VORNAME") String vorname,
                        @JsonProperty("NACHNAME") String nachname, @JsonProperty("EMAIL") String email,
                        @JsonProperty("USER_ID") String userId, @JsonProperty("ORG_CODE") String orgCode,
                        @JsonProperty("EMP_PERCENT") BigDecimal empPercent,
                        @JsonProperty("CONTRACT_ID") String contractId,
                        @JsonProperty("CONTRACT_START") LocalDate contractStart,
                        @JsonProperty("CONTRACT_END") LocalDate contractEnd,
                        @JsonProperty("CONTRACT_TYPE") String contractType,
                        @JsonProperty("WORK_DAYS") String workDays,
                        @JsonProperty("SUPERVISOR_REF") String supervisorRef,
                        @JsonProperty("ACTIVE") boolean active, @JsonProperty("CHANGED_AT") Instant changedAt) {
        ExternalEmployee toExternal() {
            return new ExternalEmployee(persNr, vorname, nachname, email, userId, orgCode, empPercent, contractId,
                    contractStart, contractEnd, contractType, workDays, supervisorRef, active, changedAt);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "ops.integration.mode", havingValue = "stub", matchIfMissing = true)
    EmployeeMasterDataGateway stubPersonnelGateway(ObjectMapper json, SimulatedOutages outages) {
        List<PersonnelDto> data = read(json);
        return new EmployeeMasterDataGateway() {
            @Override
            public Optional<ExternalEmployee> findEmployee(String personnelNumber) {
                outages.check(ExternalSystem.PERSONNEL_ERP);
                return data.stream().filter(d -> d.persNr().equals(personnelNumber)).findFirst()
                        .map(PersonnelDto::toExternal);
            }

            @Override
            public List<ExternalEmployee> findChangedEmployees(Instant since) {
                outages.check(ExternalSystem.PERSONNEL_ERP);
                return data.stream().filter(d -> d.changedAt().isAfter(since)).map(PersonnelDto::toExternal).toList();
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "ops.integration.mode", havingValue = "http")
    EmployeeMasterDataGateway httpPersonnelGateway(RestClient mockErpClient) {
        return new EmployeeMasterDataGateway() {
            @Override
            public Optional<ExternalEmployee> findEmployee(String personnelNumber) {
                return HttpErrors.call(ExternalSystem.PERSONNEL_ERP, () -> {
                    try {
                        return Optional.ofNullable(mockErpClient.get()
                                .uri("/mock/personnel/employees/{nr}", personnelNumber).retrieve()
                                .body(PersonnelDto.class)).map(PersonnelDto::toExternal);
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                            return Optional.empty();
                        }
                        throw e;
                    }
                });
            }

            @Override
            public List<ExternalEmployee> findChangedEmployees(Instant since) {
                List<PersonnelDto> dtos = HttpErrors.call(ExternalSystem.PERSONNEL_ERP, () -> mockErpClient.get()
                        .uri("/mock/personnel/changes?since={since}", since.toString()).retrieve()
                        .body(new ParameterizedTypeReference<List<PersonnelDto>>() { }));
                return dtos == null ? List.of() : dtos.stream().map(PersonnelDto::toExternal).toList();
            }
        };
    }

    private static List<PersonnelDto> read(ObjectMapper json) {
        try (InputStream in = new ClassPathResource("mock-data/personnel-changes.json").getInputStream()) {
            return json.readValue(in, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
