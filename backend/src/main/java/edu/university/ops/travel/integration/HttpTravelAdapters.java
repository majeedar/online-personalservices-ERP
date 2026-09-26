package edu.university.ops.travel.integration;

import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.ExternalSystemException;
import edu.university.ops.shared.integration.HttpErrors;
import edu.university.ops.travel.domain.TravelPorts.ApprovedTravel;
import edu.university.ops.travel.domain.TravelPorts.ExternalSettlementReference;
import edu.university.ops.travel.domain.TravelPorts.ExternalTravelReference;
import edu.university.ops.travel.domain.TravelPorts.FinanceGateway;
import edu.university.ops.travel.domain.TravelPorts.FinancePosting;
import edu.university.ops.travel.domain.TravelPorts.FinancePostingResult;
import edu.university.ops.travel.domain.TravelPorts.TravelErpGateway;
import edu.university.ops.travel.domain.TravelPorts.TravelSettlement;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * HTTP mode: adapters to the mock-erp container. The wire format (external field
 * names) is mapped here and never leaks into the domain (AGENT.md §24).
 */
@Configuration
@ConditionalOnProperty(name = "ops.integration.mode", havingValue = "http")
class HttpTravelAdapters {

    /** mock-erp finance wire format. */
    record CostCentreDto(String KOSTL, String KTEXT, boolean ACTIVE) {
    }

    record PostingDto(String BELNR) {
    }

    record TravelRefDto(String REISENR) {
    }

    @Bean
    FinanceGateway httpFinanceGateway(RestClient mockErpClient) {
        return new FinanceGateway() {
            @Override
            public boolean validateCostCentre(String costCentre) {
                return HttpErrors.call(ExternalSystem.FINANCE_ERP, () -> {
                    try {
                        CostCentreDto dto = mockErpClient.get().uri("/mock/finance/cost-centres/{code}", costCentre)
                                .retrieve().body(CostCentreDto.class);
                        return dto != null && dto.ACTIVE();
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                            return false;
                        }
                        throw e;
                    }
                });
            }

            @Override
            public FinancePostingResult postTravelSettlement(FinancePosting p) {
                Map<String, Object> body = Map.of("PERNR", p.personnelNumber(), "KOSTL", p.costCentre(),
                        "PROJEKT", p.projectCode() == null ? "" : p.projectCode(), "BETRAG", p.amount(),
                        "WAERS", p.currency(), "BUDAT", p.postingDate().toString(),
                        "REFERENZ", p.travelRequestId().toString());
                PostingDto dto = HttpErrors.call(ExternalSystem.FINANCE_ERP, () -> mockErpClient.post()
                        .uri("/mock/finance/postings").header("Idempotency-Key", p.idempotencyKey()).body(body)
                        .retrieve().body(PostingDto.class));
                return new FinancePostingResult(require(dto == null ? null : dto.BELNR(), ExternalSystem.FINANCE_ERP));
            }
        };
    }

    @Bean
    TravelErpGateway httpTravelErpGateway(RestClient mockErpClient) {
        return new TravelErpGateway() {
            @Override
            public ExternalTravelReference exportApprovedTravel(ApprovedTravel t) {
                Map<String, Object> body = Map.of("PERNR", t.personnelNumber(), "ZWECK", t.purpose(),
                        "ZIELORT", t.destinationCity(), "LAND", t.destinationCountry(), "BEGINN", t.start().toString(),
                        "ENDE", t.end().toString(), "KOSTEN", t.estimatedCost(), "WAERS", t.currency(),
                        "KOSTL", t.costCentre());
                TravelRefDto dto = HttpErrors.call(ExternalSystem.TRAVEL_ERP, () -> mockErpClient.post()
                        .uri("/mock/travel/export").header("Idempotency-Key", t.idempotencyKey()).body(body)
                        .retrieve().body(TravelRefDto.class));
                return new ExternalTravelReference(require(dto == null ? null : dto.REISENR(),
                        ExternalSystem.TRAVEL_ERP));
            }

            @Override
            public ExternalSettlementReference exportSettlement(TravelSettlement s) {
                Map<String, Object> body = Map.of("REISENR", s.externalTravelReference(), "BETRAG",
                        s.amount() == null ? BigDecimal.ZERO : s.amount(), "WAERS", s.currency());
                TravelRefDto dto = HttpErrors.call(ExternalSystem.TRAVEL_ERP, () -> mockErpClient.post()
                        .uri("/mock/travel/settlement").header("Idempotency-Key", s.idempotencyKey()).body(body)
                        .retrieve().body(TravelRefDto.class));
                return new ExternalSettlementReference(require(dto == null ? null : dto.REISENR(),
                        ExternalSystem.TRAVEL_ERP));
            }
        };
    }

    /** Validates the external reference format before it is stored (AGENT.md §25). */
    static String require(String reference, ExternalSystem system) {
        if (reference == null || !reference.matches("[A-Z]{3}-[0-9]{4,10}")) {
            throw new ExternalSystemException(system, "INVALID_REFERENCE",
                    system + " returned an invalid reference: " + reference);
        }
        return reference;
    }
}
