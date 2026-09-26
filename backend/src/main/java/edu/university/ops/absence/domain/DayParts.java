package edu.university.ops.absence.domain;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.time.LocalDate;

/**
 * The day parts of a request's first and last day. A multi-day absence may start in
 * the afternoon and end at noon; a single day is a full day, a morning or an afternoon.
 */
public record DayParts(DayPart start, DayPart end) {

    public static final DayParts FULL = new DayParts(DayPart.FULL, DayPart.FULL);

    /** Validates and normalises (missing = full day; a single day uses the half that was given). */
    public static DayParts of(LocalDate startDate, LocalDate endDate, DayPart start, DayPart end) {
        DayPart s = DayPart.orFull(start);
        DayPart e = DayPart.orFull(end);
        if (startDate != null && startDate.equals(endDate)) {
            if (s.isHalf() && e.isHalf() && s != e) {
                throw new BusinessException(ErrorCode.INVALID_DAY_PART,
                        "A single day is either a full day, a morning or an afternoon.");
            }
            DayPart part = s.isHalf() ? s : e;
            return new DayParts(part, part);
        }
        if (s == DayPart.MORNING || e == DayPart.AFTERNOON) {
            throw new BusinessException(ErrorCode.INVALID_DAY_PART,
                    "An absence over several days can only start in the afternoon and end at noon.");
        }
        return new DayParts(s, e);
    }

    /** The part covered on a date of the range {@code startDate..endDate}. */
    public DayPart on(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (date.equals(startDate)) {
            return start;
        }
        return date.equals(endDate) ? end : DayPart.FULL;
    }

    public boolean isFull() {
        return !start.isHalf() && !end.isHalf();
    }
}
