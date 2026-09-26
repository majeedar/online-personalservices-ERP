package edu.university.ops.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "work_schedule_day")
public class WorkScheduleDay {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private DayOfWeek weekday;

    private int targetMinutes;
    private boolean workingDay;

    protected WorkScheduleDay() {
    }

    WorkScheduleDay(DayOfWeek weekday, int targetMinutes, boolean workingDay) {
        this.id = UUID.randomUUID();
        this.weekday = weekday;
        this.targetMinutes = targetMinutes;
        this.workingDay = workingDay;
    }

    public UUID getId() {
        return id;
    }

    public DayOfWeek getWeekday() {
        return weekday;
    }

    public int getTargetMinutes() {
        return targetMinutes;
    }

    public boolean isWorkingDay() {
        return workingDay;
    }
}
