package edu.university.ops.travel.integration;

import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.SimulatedOutages;
import edu.university.ops.travel.domain.TravelPorts.ApprovedTravel;
import edu.university.ops.travel.domain.TravelPorts.ExternalSettlementReference;
import edu.university.ops.travel.domain.TravelPorts.ExternalTravelReference;
import edu.university.ops.travel.domain.TravelPorts.FinanceGateway;
import edu.university.ops.travel.domain.TravelPorts.FinancePosting;
import edu.university.ops.travel.domain.TravelPorts.FinancePostingResult;
import edu.university.ops.travel.domain.TravelPorts.TravelErpGateway;
import edu.university.ops.travel.domain.TravelPorts.TravelSettlement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stub mode (tests, local development without the mock-erp container):
 * in-memory finance and travel ERP with the same behaviour as mock-erp, including
 * idempotent postings and simulated outages.
 */
@Configuration
@ConditionalOnProperty(name = "ops.integration.mode", havingValue = "stub", matchIfMissing = true)
class StubTravelAdapters {

    static final Set<String> COST_CENTRES = Set.of("CC-1000", "CC-1100", "CC-1110", "CC-1120", "CC-1130", "CC-2000",
            "CC-2100", "CC-2200", "CC-2201", "CC-3000", "CC-3100", "CC-3200");

    @Bean
    FinanceGateway stubFinanceGateway(SimulatedOutages outages) {
        Map<String, String> postings = new ConcurrentHashMap<>();
        AtomicInteger sequence = new AtomicInteger(1000);
        return new FinanceGateway() {
            @Override
            public boolean validateCostCentre(String costCentre) {
                outages.check(ExternalSystem.FINANCE_ERP);
                return COST_CENTRES.contains(costCentre);
            }

            @Override
            public FinancePostingResult postTravelSettlement(FinancePosting posting) {
                outages.check(ExternalSystem.FINANCE_ERP);
                // Idempotent: a retried posting returns the original reference (AGENT.md §29).
                return new FinancePostingResult(postings.computeIfAbsent(posting.idempotencyKey(),
                        k -> "FIN-" + sequence.incrementAndGet()));
            }
        };
    }

    @Bean
    TravelErpGateway stubTravelErpGateway(SimulatedOutages outages) {
        Map<String, String> exports = new ConcurrentHashMap<>();
        AtomicInteger sequence = new AtomicInteger(5000);
        return new TravelErpGateway() {
            @Override
            public ExternalTravelReference exportApprovedTravel(ApprovedTravel travel) {
                outages.check(ExternalSystem.TRAVEL_ERP);
                return new ExternalTravelReference(exports.computeIfAbsent(travel.idempotencyKey(),
                        k -> "TRV-" + sequence.incrementAndGet()));
            }

            @Override
            public ExternalSettlementReference exportSettlement(TravelSettlement settlement) {
                outages.check(ExternalSystem.TRAVEL_ERP);
                return new ExternalSettlementReference(exports.computeIfAbsent(settlement.idempotencyKey(),
                        k -> "STL-" + sequence.incrementAndGet()));
            }
        };
    }
}
