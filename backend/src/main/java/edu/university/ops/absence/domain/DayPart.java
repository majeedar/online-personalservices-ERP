package edu.university.ops.absence.domain;

import java.math.BigDecimal;

/** Which part of a day an absence covers. Half days count 0.5 and credit half the planned time. */
public enum DayPart {
    FULL, MORNING, AFTERNOON;

    private static final BigDecimal HALF = new BigDecimal("0.5");

    public boolean isHalf() {
        return this != FULL;
    }

    /** Share of a working day: 1 for a full day, 0.5 for a half day. */
    public BigDecimal fraction() {
        return isHalf() ? HALF : BigDecimal.ONE;
    }

    /** Minutes of a day's target covered by this part. */
    public int share(int minutes) {
        return isHalf() ? minutes / 2 : minutes;
    }

    /** Two absences may share a date only if one covers the morning and the other the afternoon. */
    public boolean complements(DayPart other) {
        return (this == MORNING && other == AFTERNOON) || (this == AFTERNOON && other == MORNING);
    }

    public static DayPart orFull(DayPart part) {
        return part == null ? FULL : part;
    }
}
