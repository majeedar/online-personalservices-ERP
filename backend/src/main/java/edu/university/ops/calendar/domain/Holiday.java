package edu.university.ops.calendar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    private UUID id;

    private LocalDate date;
    private String name;
    private String regionCode;

    protected Holiday() {
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public String getRegionCode() {
        return regionCode;
    }
}
