package edu.university.ops.absence.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Absence request lifecycle (AGENT.md §13.5, §13.8) with its transition rules
 * centralised in one place (AGENT.md §80).
 */
public enum AbsenceStatus {
    DRAFT,
    SUBMITTED,
    IN_APPROVAL,
    APPROVED,
    REJECTED,
    CANCEL_REQUESTED,
    CANCELLED;

    private static final Map<AbsenceStatus, Set<AbsenceStatus>> ALLOWED = new EnumMap<>(AbsenceStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(SUBMITTED, CANCELLED));
        ALLOWED.put(SUBMITTED, EnumSet.of(IN_APPROVAL, APPROVED));
        ALLOWED.put(IN_APPROVAL, EnumSet.of(APPROVED, REJECTED, DRAFT, CANCELLED));
        ALLOWED.put(APPROVED, EnumSet.of(CANCEL_REQUESTED, CANCELLED));
        ALLOWED.put(CANCEL_REQUESTED, EnumSet.of(CANCELLED, APPROVED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(AbsenceStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(AbsenceStatus.class));
    }

    public boolean canTransitionTo(AbsenceStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Statuses that occupy the calendar: new requests must not overlap them. */
    public static final Set<AbsenceStatus> BLOCKING = EnumSet.of(SUBMITTED, IN_APPROVAL, APPROVED, CANCEL_REQUESTED);

    /** Statuses in which the absence is in effect (affects time accounts). */
    public static final Set<AbsenceStatus> EFFECTIVE = EnumSet.of(APPROVED, CANCEL_REQUESTED);
}
