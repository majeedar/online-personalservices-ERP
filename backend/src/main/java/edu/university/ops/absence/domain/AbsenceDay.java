package edu.university.ops.absence.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One calendar day of an absence request (AGENT.md §13.6). Stored for every day of
 * the range, so the user can see why a day does or does not count.
 */
@Entity
@Table(name = "absence_day")
public class AbsenceDay {

    public enum Kind { WORKING_DAY, NON_WORKING_DAY, HOLIDAY, NO_SCHEDULE }

    @Id
    private UUID id;

    private LocalDate date;
    private int plannedMinutes;
    private int creditedMinutes;
    private BigDecimal entitlementDeduction;

    @Enumerated(EnumType.STRING)
    private Kind dayKind;

    @Enumerated(EnumType.STRING)
    private DayPart dayPart = DayPart.FULL;

    protected AbsenceDay() {
    }

    AbsenceDay(AbsenceDayCalculator.CalculatedDay day) {
        this.id = UUID.randomUUID();
        this.date = day.date();
        apply(day);
    }

    void apply(AbsenceDayCalculator.CalculatedDay day) {
        this.plannedMinutes = day.plannedMinutes();
        this.creditedMinutes = day.creditedMinutes();
        this.entitlementDeduction = day.entitlementDeduction();
        this.dayKind = day.kind();
        this.dayPart = day.part();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public int getCreditedMinutes() {
        return creditedMinutes;
    }

    public BigDecimal getEntitlementDeduction() {
        return entitlementDeduction;
    }

    public Kind getDayKind() {
        return dayKind;
    }

    public DayPart getDayPart() {
        return dayPart;
    }

    /** Working days this day contributes: 1, 0.5 or 0. */
    public BigDecimal workingDayShare() {
        return dayKind == Kind.WORKING_DAY ? dayPart.fraction() : BigDecimal.ZERO;
    }
}
