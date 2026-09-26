package edu.university.ops.travel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read API for reporting (AGENT.md §81): travel by status, estimated vs actual cost. */
public interface TravelReports {

    List<TravelSummary> allTrips();

    record TravelSummary(UUID id, UUID employeeId, String status, String destinationCity, Instant start,
                         BigDecimal estimatedCost, BigDecimal settledAmount, String currency, String costCentre) {
    }
}
