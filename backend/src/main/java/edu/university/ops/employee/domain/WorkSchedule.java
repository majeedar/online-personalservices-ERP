package edu.university.ops.employee.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Weekly working-time pattern valid for a period (AGENT.md §13.2). */
@Entity
@Table(name = "work_schedule")
public class WorkSchedule {

    @Id
    private UUID id;

    private UUID employeeId;
    private LocalDate validFrom;
    private LocalDate validTo;
    private int weeklyTargetMinutes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_schedule_id", nullable = false)
    private List<WorkScheduleDay> days = new ArrayList<>();

    protected WorkSchedule() {
    }

    /** A schedule with the given target minutes per weekday (0 = non-working day). */
    public static WorkSchedule of(UUID employeeId, LocalDate validFrom, Map<DayOfWeek, Integer> targets) {
        WorkSchedule s = new WorkSchedule();
        s.id = UUID.randomUUID();
        s.employeeId = employeeId;
        s.validFrom = validFrom;
        for (DayOfWeek day : DayOfWeek.values()) {
            int minutes = targets.getOrDefault(day, 0);
            s.days.add(new WorkScheduleDay(day, minutes, minutes > 0));
            s.weeklyTargetMinutes += minutes;
        }
        return s;
    }

    /** Ends this schedule the day before a successor starts. */
    public void endBefore(LocalDate successorStart) {
        this.validTo = successorStart.minusDays(1);
    }

    /** True if both schedules plan the same minutes on every weekday. */
    public boolean samePatternAs(Map<DayOfWeek, Integer> targets) {
        return days.stream().allMatch(d -> d.getTargetMinutes() == targets.getOrDefault(d.getWeekday(), 0));
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public int getWeeklyTargetMinutes() {
        return weeklyTargetMinutes;
    }

    public List<WorkScheduleDay> getDays() {
        return List.copyOf(days);
    }
}
