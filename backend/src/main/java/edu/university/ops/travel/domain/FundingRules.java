package edu.university.ops.travel.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Split-funding validation (AGENT.md §14.3), pure and deterministic.
 *
 * <ul>
 *   <li>Shares are either all percentages or all amounts, never mixed.</li>
 *   <li>Percentages total exactly 100.</li>
 *   <li>Amounts must not exceed the expected total, when that limit is configured.</li>
 *   <li>Every funding source exists and is active; each is used at most once.</li>
 * </ul>
 */
public final class FundingRules {

    private FundingRules() {
    }

    public static List<String> violations(List<TravelFunding> fundings, Map<UUID, FundingSource> sources,
                                          BigDecimal estimatedCost, boolean limitAmountsToEstimate) {
        List<String> problems = new ArrayList<>();
        if (fundings.isEmpty()) {
            return problems;
        }
        long withPercentage = fundings.stream().filter(f -> f.getPercentage() != null).count();
        long withAmount = fundings.stream().filter(f -> f.getAmount() != null).count();
        if (withPercentage > 0 && withAmount > 0 || withPercentage + withAmount != fundings.size()) {
            problems.add("Give every funding share either as a percentage or as an amount, not both.");
            return problems;
        }
        if (fundings.stream().map(TravelFunding::getFundingSourceId).distinct().count() != fundings.size()) {
            problems.add("Each funding source may be used only once.");
        }
        for (TravelFunding f : fundings) {
            FundingSource source = sources.get(f.getFundingSourceId());
            if (source == null || !source.isActive()) {
                problems.add("A funding source is unknown or no longer active.");
                break;
            }
        }
        if (withPercentage > 0) {
            BigDecimal total = fundings.stream().map(TravelFunding::getPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.valueOf(100)) != 0) {
                problems.add("Funding percentages must total 100 (currently " + total.stripTrailingZeros()
                        .toPlainString() + ").");
            }
        } else if (limitAmountsToEstimate) {
            BigDecimal total = fundings.stream().map(TravelFunding::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(estimatedCost) > 0) {
                problems.add("Funding amounts exceed the estimated cost.");
            }
        }
        return problems;
    }
}
