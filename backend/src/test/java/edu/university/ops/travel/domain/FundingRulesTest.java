package edu.university.ops.travel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FundingRulesTest {

    static final UUID BUDGET = UUID.randomUUID();
    static final UUID PROJECT = UUID.randomUUID();

    static FundingSource source(boolean active) throws Exception {
        Constructor<FundingSource> c = FundingSource.class.getDeclaredConstructor();
        c.setAccessible(true);
        FundingSource s = c.newInstance();
        var field = FundingSource.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(s, active);
        return s;
    }

    static List<String> check(List<TravelFunding> fundings, boolean projectActive) throws Exception {
        return FundingRules.violations(fundings, Map.of(BUDGET, source(true), PROJECT, source(projectActive)),
                new BigDecimal("500"), true);
    }

    static TravelFunding pct(UUID source, String value) {
        return new TravelFunding(source, new BigDecimal(value), null);
    }

    static TravelFunding amount(UUID source, String value) {
        return new TravelFunding(source, null, new BigDecimal(value));
    }

    @Test
    void percentagesMustTotalHundred() throws Exception {
        assertThat(check(List.of(pct(BUDGET, "60"), pct(PROJECT, "40")), true)).isEmpty();
        assertThat(check(List.of(pct(BUDGET, "50"), pct(PROJECT, "30")), true))
                .singleElement().asString().contains("total 100");
    }

    @Test
    void amountsMustNotExceedTheEstimate() throws Exception {
        assertThat(check(List.of(amount(BUDGET, "300"), amount(PROJECT, "200")), true)).isEmpty();
        assertThat(check(List.of(amount(BUDGET, "300"), amount(PROJECT, "250")), true))
                .singleElement().asString().contains("exceed");
    }

    @Test
    void mixingSharesInactiveSourcesAndDuplicatesAreRejected() throws Exception {
        assertThat(check(List.of(pct(BUDGET, "50"), amount(PROJECT, "100")), true))
                .singleElement().asString().contains("not both");
        assertThat(check(List.of(pct(BUDGET, "50"), pct(PROJECT, "50")), false)).anyMatch(p -> p.contains("active"));
        assertThat(check(List.of(pct(BUDGET, "50"), pct(BUDGET, "50")), true)).anyMatch(p -> p.contains("once"));
    }

    @Test
    void noSplitIsValid() throws Exception {
        assertThat(check(List.of(), true)).isEmpty();
    }
}
