package edu.university.ops.travel.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ports of the travel module (ADR-002): persistence and the two external systems
 * (AGENT.md §22). External representations never leak into the domain; adapters
 * map them to these records.
 */
public final class TravelPorts {

    private TravelPorts() {
    }

    /** Outbox event types of the travel exports (ADR-006). */
    public static final class ExportTypes {
        public static final String TRAVEL_EXPORT = "TRAVEL_EXPORT";
        public static final String SETTLEMENT_EXPORT = "TRAVEL_SETTLEMENT_EXPORT";
        public static final String FINANCE_POSTING = "FINANCE_POSTING";

        private ExportTypes() {
        }
    }

    public interface TravelRequestRepository {
        TravelRequest save(TravelRequest request);

        Optional<TravelRequest> findById(UUID id);

        List<TravelRequest> findByEmployeeIdOrderByStartDateTimeDesc(UUID employeeId);

        List<TravelRequest> findByStatusIn(Collection<TravelStatus> statuses);

        /** Finished, not yet anonymised trips that ended before {@code endedBefore} (retention). */
        List<TravelRequest> findByStatusInAndEndDateTimeBeforeAndAnonymisedAtIsNull(Collection<TravelStatus> statuses,
                                                                                 Instant endedBefore);

        List<TravelRequest> findAll();

        long count();
    }

    public interface FundingSourceRepository {
        List<FundingSource> findByActiveTrueOrderByCostCentre();

        List<FundingSource> findByIdIn(Collection<UUID> ids);
    }

    /** Finance ERP (AGENT.md §22). */
    public interface FinanceGateway {

        /** @throws edu.university.ops.shared.integration.ExternalSystemException if finance is unreachable */
        boolean validateCostCentre(String costCentre);

        FinancePostingResult postTravelSettlement(FinancePosting posting);
    }

    public record FinancePosting(String idempotencyKey, UUID travelRequestId, String personnelNumber,
                                 String costCentre, String projectCode, BigDecimal amount, String currency,
                                 LocalDate postingDate) {
    }

    public record FinancePostingResult(String postingReference) {
    }

    /** Travel ERP (AGENT.md §22). */
    public interface TravelErpGateway {

        ExternalTravelReference exportApprovedTravel(ApprovedTravel travel);

        ExternalSettlementReference exportSettlement(TravelSettlement settlement);
    }

    public record ApprovedTravel(String idempotencyKey, UUID travelRequestId, String personnelNumber, String purpose,
                                 String destinationCity, String destinationCountry, Instant start, Instant end,
                                 BigDecimal estimatedCost, String currency, String costCentre) {
    }

    public record TravelSettlement(String idempotencyKey, String externalTravelReference, UUID travelRequestId,
                                   BigDecimal amount, String currency) {
    }

    public record ExternalTravelReference(String reference) {
    }

    public record ExternalSettlementReference(String reference) {
    }
}
