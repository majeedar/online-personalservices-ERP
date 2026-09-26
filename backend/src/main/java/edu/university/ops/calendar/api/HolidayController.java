package edu.university.ops.calendar.api;

import edu.university.ops.calendar.HolidayCalendar;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Calendar")
class HolidayController {

    private final HolidayCalendar calendar;

    HolidayController(HolidayCalendar calendar) {
        this.calendar = calendar;
    }

    record HolidayResponse(LocalDate date, String name) {
    }

    @GetMapping
    @Operation(summary = "Public holidays of a year in the configured region")
    List<HolidayResponse> list(@RequestParam int year) {
        return calendar.holidaysBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).entrySet().stream()
                .map(e -> new HolidayResponse(e.getKey(), e.getValue())).toList();
    }
}
