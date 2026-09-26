package edu.university.ops.travel.application;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Travel rules that differ between universities (AGENT.md §48).
 *
 * @param currencies                  accepted currencies
 * @param financialApprovalThreshold  trips with an estimated cost above this need financial approval
 * @param limitFundingToEstimate      funding amounts may not exceed the estimated cost
 * @param receiptRequiredTypes        expense types that need a receipt before submission
 * @param maxDaysInPast               how far back a trip may start when it is requested
 */
@ConfigurationProperties(prefix = "ops.travel")
public record TravelProperties(@DefaultValue({"EUR", "USD", "GBP", "CHF"}) Set<String> currencies,
                               @DefaultValue("0") BigDecimal financialApprovalThreshold,
                               @DefaultValue("true") boolean limitFundingToEstimate,
                               @DefaultValue({"TRAIN", "FLIGHT", "HOTEL", "TAXI", "CONFERENCE_FEE"})
                               Set<String> receiptRequiredTypes,
                               @DefaultValue("30") int maxDaysInPast) {
}
