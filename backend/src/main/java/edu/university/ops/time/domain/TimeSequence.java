package edu.university.ops.time.domain;

import edu.university.ops.time.domain.TimeEntry.Type;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Valid order of clock events within a day (AGENT.md §44 "buttons must disable
 * invalid transitions" — enforced here, on the server).
 *
 * <pre>
 * (start) ──CLOCK_IN──► WORKING ──BREAK_START──► ON_BREAK ──BREAK_END──► WORKING
 *                          └──CLOCK_OUT──► (start)
 * </pre>
 */
public final class TimeSequence {

    private TimeSequence() {
    }

    public enum State { OFF, WORKING, ON_BREAK }

    public record Event(Instant timestamp, Type type) {
    }

    public static State stateAfter(List<Event> orderedEvents) {
        State state = State.OFF;
        for (Event e : orderedEvents) {
            state = next(state, e.type());
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    /** @return the state after {@code type}, or null if {@code type} is not allowed in {@code state} */
    public static State next(State state, Type type) {
        return switch (state) {
            case OFF -> type == Type.CLOCK_IN ? State.WORKING : null;
            case WORKING -> switch (type) {
                case BREAK_START -> State.ON_BREAK;
                case CLOCK_OUT -> State.OFF;
                default -> null;
            };
            case ON_BREAK -> type == Type.BREAK_END ? State.WORKING : null;
        };
    }

    public static Set<Type> allowedNext(State state) {
        Set<Type> allowed = EnumSet.noneOf(Type.class);
        for (Type t : Type.values()) {
            if (next(state, t) != null) {
                allowed.add(t);
            }
        }
        return allowed;
    }

    /** A whole day is valid if every event is allowed and timestamps strictly increase. */
    public static boolean isValidDay(List<Event> orderedEvents) {
        for (int i = 1; i < orderedEvents.size(); i++) {
            if (!orderedEvents.get(i).timestamp().isAfter(orderedEvents.get(i - 1).timestamp())) {
                return false;
            }
        }
        return stateAfter(orderedEvents) != null;
    }
}
