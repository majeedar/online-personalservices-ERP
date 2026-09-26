package edu.university.ops.calendar.application;

import edu.university.ops.calendar.HolidayCalendar;
import edu.university.ops.calendar.domain.Holiday;
import edu.university.ops.calendar.domain.HolidayRepository;
import edu.university.ops.shared.configuration.OpsProperties;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HolidayCalendarService implements HolidayCalendar {

    private final HolidayRepository holidays;
    private final OpsProperties properties;

    HolidayCalendarService(HolidayRepository holidays, OpsProperties properties) {
        this.holidays = holidays;
        this.properties = properties;
    }

    @Override
    public Map<LocalDate, String> holidaysBetween(LocalDate from, LocalDate to) {
        Map<LocalDate, String> result = new LinkedHashMap<>();
        for (Holiday h : holidays.findByRegionCodeAndDateBetweenOrderByDate(properties.holidayRegion(), from, to)) {
            result.put(h.getDate(), h.getName());
        }
        return result;
    }
}
