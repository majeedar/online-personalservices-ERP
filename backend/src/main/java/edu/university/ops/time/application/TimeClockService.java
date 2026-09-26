package edu.university.ops.time.application;

import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.time.domain.TimeRepositories.TimeEntryRepository;
import edu.university.ops.time.domain.TimeSequence;
import edu.university.ops.time.domain.TimeSequence.Event;
import edu.university.ops.time.domain.TimeSequence.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Clock in/out and breaks (AGENT.md §15.1, §37). */
@Service
@Transactional
public class TimeClockService {

    /** A shift may continue past midnight; events up to this long after the last one stay on its day. */
    static final Duration MAX_OPEN_SHIFT = Duration.ofHours(16);

    public record TimeEntryCreated(UUID entryId, UUID employeeId, LocalDate businessDate, TimeEntry.Type type) {
    }

    public record DayState(LocalDate date, State state, Set<TimeEntry.Type> allowedNext, List<TimeEntry> entries) {
    }

    private final TimeEntryRepository entries;
    private final TimeAccountService accounts;
    private final ApplicationEventPublisher events;
    private final OpsProperties properties;
    private final Clock clock;

    TimeClockService(TimeEntryRepository entries, TimeAccountService accounts, ApplicationEventPublisher events,
                     OpsProperties properties, Clock clock) {
        this.entries = entries;
        this.accounts = accounts;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Records an event now, from the web UI. */
    public TimeEntry record(UUID employeeId, TimeEntry.Type type) {
        return record(employeeId, Instant.now(clock), type, TimeEntry.Source.WEB);
    }

    /**
     * Records an event at a given time (terminal import, demo data). The event must
     * continue the day's sequence; earlier entries are corrected via correction requests.
     */
    public TimeEntry record(UUID employeeId, Instant at, TimeEntry.Type type, TimeEntry.Source source) {
        LocalDate date = businessDateFor(employeeId, at);
        if (accounts.isClosed(date)) {
            throw new BusinessException(ErrorCode.TIME_MONTH_CLOSED,
                    "The time accounts for " + YearMonth.from(date) + " are closed.");
        }
        List<TimeEntry> day = activeEntries(employeeId, date);
        State state = TimeSequence.stateAfter(events(day));
        if (state == null || TimeSequence.next(state, type) == null) {
            throw new BusinessException(ErrorCode.TIME_SEQUENCE_INVALID, message(state, type));
        }
        if (!day.isEmpty() && !at.isAfter(day.getLast().getTimestamp())) {
            throw new BusinessException(ErrorCode.TIME_SEQUENCE_INVALID,
                    "The time must be after the last recorded entry.");
        }
        TimeEntry entry = entries.save(new TimeEntry(employeeId, at, date, type, source, Instant.now(clock), null));
        accounts.recalculateDay(employeeId, date);
        events.publishEvent(new TimeEntryCreated(entry.getId(), employeeId, date, type));
        return entry;
    }

    @Transactional(readOnly = true)
    public DayState today(UUID employeeId) {
        Instant now = Instant.now(clock);
        LocalDate date = businessDateFor(employeeId, now);
        List<TimeEntry> day = activeEntries(employeeId, date);
        State state = TimeSequence.stateAfter(events(day));
        return new DayState(date, state, TimeSequence.allowedNext(state == null ? State.OFF : state), day);
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> history(UUID employeeId, LocalDate date) {
        return entries.findByEmployeeIdAndBusinessDateOrderByTimestamp(employeeId, date);
    }

    List<TimeEntry> activeEntries(UUID employeeId, LocalDate date) {
        return entries.findByEmployeeIdAndBusinessDateAndVoidedAtIsNullOrderByTimestamp(employeeId, date);
    }

    /**
     * The calendar date in the university timezone (ADR-010), except that an event
     * continuing a shift started the previous day (night shift) belongs to that day.
     */
    LocalDate businessDateFor(UUID employeeId, Instant at) {
        LocalDate calendarDate = LocalDate.ofInstant(at, properties.timezone());
        if (!activeEntries(employeeId, calendarDate).isEmpty()) {
            return calendarDate;
        }
        LocalDate previous = calendarDate.minusDays(1);
        List<TimeEntry> previousDay = activeEntries(employeeId, previous);
        State state = TimeSequence.stateAfter(events(previousDay));
        boolean openShift = state == State.WORKING || state == State.ON_BREAK;
        if (openShift && Duration.between(previousDay.getLast().getTimestamp(), at).compareTo(MAX_OPEN_SHIFT) < 0) {
            return previous;
        }
        return calendarDate;
    }

    static List<Event> events(List<TimeEntry> entries) {
        return entries.stream().map(e -> new Event(e.getTimestamp(), e.getType())).toList();
    }

    private static String message(State state, TimeEntry.Type type) {
        String action = switch (type) {
            case CLOCK_IN -> "clock in";
            case CLOCK_OUT -> "clock out";
            case BREAK_START -> "start a break";
            case BREAK_END -> "end a break";
        };
        String where = state == null ? "with the current entries" : switch (state) {
            case OFF -> "before clocking in";
            case WORKING -> "while working";
            case ON_BREAK -> "during a break";
        };
        return "You cannot " + action + " " + where + ".";
    }
}
