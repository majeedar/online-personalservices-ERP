package edu.university.ops.travel.integration;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.integration.ExternalSystem;
import edu.university.ops.shared.integration.ExternalSystemException;
import edu.university.ops.shared.integration.OutboxEvent;
import edu.university.ops.shared.integration.OutboxHandler;
import edu.university.ops.travel.domain.TravelPorts.ApprovedTravel;
import edu.university.ops.travel.domain.TravelPorts.ExportTypes;
import edu.university.ops.travel.domain.TravelPorts.FinanceGateway;
import edu.university.ops.travel.domain.TravelPorts.FinancePosting;
import edu.university.ops.travel.domain.TravelPorts.TravelErpGateway;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import edu.university.ops.travel.domain.TravelPorts.TravelSettlement;
import edu.university.ops.travel.domain.TravelRequest;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox handlers for the travel exports (AGENT.md §50). Each delivery carries the
 * outbox idempotency key, so retries never create a second export or posting.
 */
@Configuration
class TravelOutboxHandlers {

    @Bean
    OutboxHandler travelExportHandler(TravelRequestRepository requests, TravelErpGateway travelErp,
                                      EmployeeDirectory employees) {
        return new OutboxHandler() {
            public String eventType() {
                return ExportTypes.TRAVEL_EXPORT;
            }

            public String interfaceName() {
                return "TRAVEL_ERP_EXPORT";
            }

            public void handle(OutboxEvent event) {
                TravelRequest r = requests.findById(event.getAggregateId()).orElseThrow();
                var ref = travelErp.exportApprovedTravel(new ApprovedTravel(event.getIdempotencyKey(), r.getId(),
                        personnelNumber(employees, r), r.getPurpose(), r.getDestinationCity(),
                        r.getDestinationCountry(), r.getStartDateTime(), r.getEndDateTime(), r.getEstimatedCost(),
                        r.getCurrency(), r.getCostCentre()));
                r.recordTravelExport(ref.reference());
            }
        };
    }

    @Bean
    OutboxHandler settlementExportHandler(TravelRequestRepository requests, TravelErpGateway travelErp) {
        return new OutboxHandler() {
            public String eventType() {
                return ExportTypes.SETTLEMENT_EXPORT;
            }

            public String interfaceName() {
                return "TRAVEL_ERP_SETTLEMENT";
            }

            public void handle(OutboxEvent event) {
                TravelRequest r = requests.findById(event.getAggregateId()).orElseThrow();
                if (r.getExternalTravelReference() == null) {
                    // The travel export has not been delivered yet; try again later.
                    throw new ExternalSystemException(ExternalSystem.TRAVEL_ERP, "DEPENDENCY_PENDING",
                            "Travel export not yet delivered");
                }
                var ref = travelErp.exportSettlement(new TravelSettlement(event.getIdempotencyKey(),
                        r.getExternalTravelReference(), r.getId(), r.getSettledAmount(), r.getCurrency()));
                r.recordSettlementExport(ref.reference());
            }
        };
    }

    @Bean
    OutboxHandler financePostingHandler(TravelRequestRepository requests, FinanceGateway finance,
                                        EmployeeDirectory employees, Clock clock) {
        return new OutboxHandler() {
            public String eventType() {
                return ExportTypes.FINANCE_POSTING;
            }

            public String interfaceName() {
                return "FINANCE_POSTING";
            }

            public void handle(OutboxEvent event) {
                TravelRequest r = requests.findById(event.getAggregateId()).orElseThrow();
                var result = finance.postTravelSettlement(new FinancePosting(event.getIdempotencyKey(), r.getId(),
                        personnelNumber(employees, r), r.getCostCentre(), r.getProjectCode(), r.getSettledAmount(),
                        r.getCurrency(), LocalDate.now(clock)));
                r.recordFinancePosting(result.postingReference());
            }
        };
    }

    private static String personnelNumber(EmployeeDirectory employees, TravelRequest r) {
        return employees.findEmployee(r.getEmployeeId()).map(EmployeeDirectory.EmployeeSummary::personnelNumber)
                .orElse("UNKNOWN");
    }
}
