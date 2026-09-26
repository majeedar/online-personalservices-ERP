package edu.university.ops.calendar.domain;

import java.time.LocalDate;
import java.util.List;

/** Persistence port; implemented in {@code calendar.persistence}. */
public interface HolidayRepository {

    List<Holiday> findByRegionCodeAndDateBetweenOrderByDate(String regionCode, LocalDate from, LocalDate to);
}
