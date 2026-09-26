package edu.university.ops.travel;

import java.math.BigDecimal;
import java.util.UUID;

/** Domain events of the travel module (AGENT.md §49). */
public final class TravelEvents {

    private TravelEvents() {
    }

    public record TravelSubmitted(UUID travelRequestId, UUID employeeId) {
    }

    public record TravelApproved(UUID travelRequestId, UUID employeeId, String step) {
    }

    public record TravelAuthorized(UUID travelRequestId, UUID employeeId) {
    }

    public record TravelSettlementCreated(UUID travelRequestId, UUID employeeId, BigDecimal amount, String currency) {
    }
}
