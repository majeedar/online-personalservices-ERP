package edu.university.ops.travel.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Travel lifecycle (AGENT.md §14.2). Supervisor and financial approval are two
 * steps of one IN_APPROVAL phase; the workflow history shows which step is active.
 */
public enum TravelStatus {
    DRAFT,
    IN_APPROVAL,
    AUTHORIZED,
    REJECTED,
    COMPLETED,
    EXPENSES_SUBMITTED,
    SETTLED,
    CANCELLED;

    private static final Map<TravelStatus, Set<TravelStatus>> ALLOWED = new EnumMap<>(TravelStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(IN_APPROVAL, CANCELLED));
        ALLOWED.put(IN_APPROVAL, EnumSet.of(AUTHORIZED, REJECTED, DRAFT, CANCELLED));
        ALLOWED.put(AUTHORIZED, EnumSet.of(COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.of(EXPENSES_SUBMITTED));
        ALLOWED.put(EXPENSES_SUBMITTED, EnumSet.of(SETTLED, COMPLETED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(TravelStatus.class));
        ALLOWED.put(SETTLED, EnumSet.noneOf(TravelStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(TravelStatus.class));
    }

    public boolean canTransitionTo(TravelStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
