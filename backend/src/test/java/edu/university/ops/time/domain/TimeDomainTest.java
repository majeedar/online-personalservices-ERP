package edu.university.ops.time.domain;

import static org.assertj.core.api.Assertions.assertThat;

import edu.university.ops.time.domain.TimeEntry.Type;
import edu.university.ops.time.domain.TimeSequence.Event;
import edu.university.ops.time.domain.TimeSequence.State;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeDomainTest {

    static Instant at(String hhmm) {
        return Instant.parse("2027-03-01T" + hhmm + ":00Z");
    }

    static Event e(String hhmm, Type type) {
        return new Event(at(hhmm), type);
    }

    @Test
    void sequenceRules() {
        assertThat(TimeSequence.allowedNext(State.OFF)).containsExactly(Type.CLOCK_IN);
        assertThat(TimeSequence.allowedNext(State.WORKING)).containsExactlyInAnyOrder(Type.BREAK_START, Type.CLOCK_OUT);
        assertThat(TimeSequence.allowedNext(State.ON_BREAK)).containsExactly(Type.BREAK_END);
        assertThat(TimeSequence.stateAfter(List.of(e("08:00", Type.CLOCK_OUT)))).isNull();
        assertThat(TimeSequence.isValidDay(List.of(e("08:00", Type.CLOCK_IN), e("07:00", Type.CLOCK_OUT)))).isFalse();
        assertThat(TimeSequence.isValidDay(List.of(e("08:00", Type.CLOCK_IN), e("12:00", Type.BREAK_START),
                e("12:30", Type.BREAK_END), e("16:30", Type.CLOCK_OUT)))).isTrue();
    }

    @Test
    void workedTimeExcludesBreaks() {
        var r = DailyTimeCalculator.calculate(List.of(e("08:00", Type.CLOCK_IN), e("12:00", Type.BREAK_START),
                e("12:30", Type.BREAK_END), e("16:30", Type.CLOCK_OUT)), 480, 0, null, true);
        assertThat(r.workedMinutes()).isEqualTo(480);
        assertThat(r.breakMinutes()).isEqualTo(30);
        assertThat(r.balanceMinutes()).isZero();
        assertThat(r.statutoryBreakApplied()).isFalse();
    }

    @Test
    void missingStatutoryBreakIsDeducted() {
        // 8 hours without any break: 30 minutes are deducted.
        var r = DailyTimeCalculator.calculate(List.of(e("08:00", Type.CLOCK_IN), e("16:00", Type.CLOCK_OUT)), 480, 0,
                null, true);
        assertThat(r.workedMinutes()).isEqualTo(450);
        assertThat(r.breakMinutes()).isEqualTo(30);
        assertThat(r.balanceMinutes()).isEqualTo(-30);
        assertThat(r.statutoryBreakApplied()).isTrue();

        var off = DailyTimeCalculator.calculate(List.of(e("08:00", Type.CLOCK_IN), e("16:00", Type.CLOCK_OUT)), 480,
                0, null, false);
        assertThat(off.workedMinutes()).isEqualTo(480);
    }

    @Test
    void approvedAbsenceIsCredited() {
        var r = DailyTimeCalculator.calculate(List.of(), 480, 480, null, true);
        assertThat(r.creditedMinutes()).isEqualTo(480);
        assertThat(r.balanceMinutes()).isZero();
    }

    @Test
    void openShiftCountsUntilNowAndIsIncomplete() {
        var live = DailyTimeCalculator.calculate(List.of(e("08:00", Type.CLOCK_IN)), 480, 0, at("10:00"), true);
        assertThat(live.workedMinutes()).isEqualTo(120);
        assertThat(live.incomplete()).isTrue();

        var past = DailyTimeCalculator.calculate(List.of(e("08:00", Type.CLOCK_IN)), 480, 0, null, true);
        assertThat(past.workedMinutes()).isZero();
        assertThat(past.balanceMinutes()).isEqualTo(-480);
    }
}
