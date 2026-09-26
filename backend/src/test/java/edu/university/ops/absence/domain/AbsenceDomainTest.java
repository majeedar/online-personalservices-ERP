package edu.university.ops.absence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.university.ops.absence.domain.AbsenceDayCalculator.CalculatedDay;
import edu.university.ops.absence.domain.AbsenceDayCalculator.DayPlan;
import edu.university.ops.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbsenceDomainTest {

    static final LeaveType ANNUAL = new LeaveType(UUID.randomUUID(), "ANNUAL_LEAVE", "Annual leave", true, true, true,
            false, true);
    static final LeaveType FLEX = new LeaveType(UUID.randomUUID(), "FLEX_DAY", "Flex day", false, true, false, false,
            true);
    static final LeaveType SICK = new LeaveType(UUID.randomUUID(), "SICK_LEAVE", "Sick leave", false, false, true,
            false, true);

    /** Mon–Thu 480 minutes, Friday and weekend off (Demo Scenario 2). */
    static final Function<LocalDate, Optional<DayPlan>> PART_TIME = date -> {
        DayOfWeek d = date.getDayOfWeek();
        boolean works = d.getValue() <= 4;
        return Optional.of(new DayPlan(works ? 480 : 0, works));
    };

    static final Function<LocalDate, Optional<DayPlan>> FULL_TIME = date -> {
        boolean works = date.getDayOfWeek().getValue() <= 5;
        return Optional.of(new DayPlan(works ? 480 : 0, works));
    };

    @Nested
    class DayCalculation {

        @Test
        void partTimeThursdayToMondayCountsOnlyThursdayAndMonday() {
            // Thu 2027-03-04 .. Mon 2027-03-08
            List<CalculatedDay> days = AbsenceDayCalculator.calculate(LocalDate.of(2027, 3, 4),
                    LocalDate.of(2027, 3, 8), ANNUAL, PART_TIME, Set.of());

            assertThat(days).hasSize(5);
            assertThat(days).filteredOn(CalculatedDay::counts).extracting(CalculatedDay::date)
                    .containsExactly(LocalDate.of(2027, 3, 4), LocalDate.of(2027, 3, 8));
            assertThat(days.get(1).kind()).isEqualTo(AbsenceDay.Kind.NON_WORKING_DAY);
            assertThat(days.stream().map(CalculatedDay::entitlementDeduction).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("2");
        }

        @Test
        void publicHolidaysDoNotCount() {
            LocalDate goodFriday = LocalDate.of(2027, 3, 26);
            LocalDate easterMonday = LocalDate.of(2027, 3, 29);
            List<CalculatedDay> days = AbsenceDayCalculator.calculate(LocalDate.of(2027, 3, 25), easterMonday, ANNUAL,
                    FULL_TIME, Set.of(goodFriday, easterMonday));

            assertThat(days).filteredOn(CalculatedDay::counts).hasSize(1);
            assertThat(days).filteredOn(d -> d.kind() == AbsenceDay.Kind.HOLIDAY).hasSize(2);
        }

        @Test
        void flexDayDeductsNothingAndCreditsNoWorkingTime() {
            CalculatedDay day = AbsenceDayCalculator.calculate(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1), FLEX,
                    FULL_TIME, Set.of()).getFirst();
            assertThat(day.plannedMinutes()).isEqualTo(480);
            assertThat(day.creditedMinutes()).isZero();
            assertThat(day.entitlementDeduction()).isEqualByComparingTo("0");
        }

        @Test
        void sickLeaveCreditsTimeWithoutDeduction() {
            CalculatedDay day = AbsenceDayCalculator.calculate(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1), SICK,
                    FULL_TIME, Set.of()).getFirst();
            assertThat(day.creditedMinutes()).isEqualTo(480);
            assertThat(day.entitlementDeduction()).isEqualByComparingTo("0");
        }

        @Test
        void daysWithoutValidScheduleDoNotCount() {
            List<CalculatedDay> days = AbsenceDayCalculator.calculate(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 2),
                    ANNUAL, date -> Optional.empty(), Set.of());
            assertThat(days).allMatch(d -> d.kind() == AbsenceDay.Kind.NO_SCHEDULE && !d.counts());
        }
    }

    @Nested
    class HalfDays {

        @Test
        void halfDayDeductsHalfAndCreditsHalfTheTarget() {
            LocalDate monday = LocalDate.of(2027, 3, 1);
            CalculatedDay day = AbsenceDayCalculator.calculate(monday, monday,
                    DayParts.of(monday, monday, DayPart.MORNING, null), ANNUAL, FULL_TIME, Set.of()).getFirst();
            assertThat(day.part()).isEqualTo(DayPart.MORNING);
            assertThat(day.plannedMinutes()).isEqualTo(240);
            assertThat(day.creditedMinutes()).isEqualTo(240);
            assertThat(day.entitlementDeduction()).isEqualByComparingTo("0.5");
            assertThat(day.workingDayShare()).isEqualByComparingTo("0.5");
        }

        @Test
        void multiDayAbsenceMayStartInTheAfternoonAndEndAtNoon() {
            // Mon afternoon .. Wed morning = 0.5 + 1 + 0.5 days
            LocalDate mon = LocalDate.of(2027, 3, 1);
            LocalDate wed = LocalDate.of(2027, 3, 3);
            List<CalculatedDay> days = AbsenceDayCalculator.calculate(mon, wed,
                    DayParts.of(mon, wed, DayPart.AFTERNOON, DayPart.MORNING), ANNUAL, FULL_TIME, Set.of());
            assertThat(days).extracting(CalculatedDay::part)
                    .containsExactly(DayPart.AFTERNOON, DayPart.FULL, DayPart.MORNING);
            assertThat(days.stream().map(CalculatedDay::entitlementDeduction).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("2");
        }

        @Test
        void halfDayOnANonWorkingDayCountsNothing() {
            LocalDate saturday = LocalDate.of(2027, 3, 6);
            CalculatedDay day = AbsenceDayCalculator.calculate(saturday, saturday,
                    DayParts.of(saturday, saturday, DayPart.AFTERNOON, null), ANNUAL, FULL_TIME, Set.of()).getFirst();
            assertThat(day.workingDayShare()).isEqualByComparingTo("0");
            assertThat(day.entitlementDeduction()).isEqualByComparingTo("0");
        }

        @Test
        void invalidDayPartsAreRejected() {
            LocalDate mon = LocalDate.of(2027, 3, 1);
            LocalDate tue = LocalDate.of(2027, 3, 2);
            assertThatThrownBy(() -> DayParts.of(mon, tue, DayPart.MORNING, null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("afternoon");
            assertThatThrownBy(() -> DayParts.of(mon, tue, null, DayPart.AFTERNOON))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> DayParts.of(mon, mon, DayPart.MORNING, DayPart.AFTERNOON))
                    .isInstanceOf(BusinessException.class);
            assertThat(DayParts.of(mon, mon, DayPart.FULL, DayPart.AFTERNOON))
                    .isEqualTo(new DayParts(DayPart.AFTERNOON, DayPart.AFTERNOON));
        }

        @Test
        void onlyMorningAndAfternoonComplementEachOther() {
            assertThat(DayPart.MORNING.complements(DayPart.AFTERNOON)).isTrue();
            assertThat(DayPart.MORNING.complements(DayPart.MORNING)).isFalse();
            assertThat(DayPart.FULL.complements(DayPart.AFTERNOON)).isFalse();
        }
    }

    @Nested
    class Lifecycle {

        AbsenceRequest draft() {
            AbsenceRequest r = AbsenceRequest.draft(UUID.randomUUID(), ANNUAL.getId(), LocalDate.of(2027, 3, 1),
                    LocalDate.of(2027, 3, 5), DayParts.FULL, null, null, Instant.EPOCH);
            r.replaceDays(AbsenceDayCalculator.calculate(r.getStartDate(), r.getEndDate(), ANNUAL, FULL_TIME,
                    Set.of()));
            return r;
        }

        @Test
        void validTransitions() {
            AbsenceRequest r = draft();
            r.submit(Instant.EPOCH);
            r.startApproval(UUID.randomUUID(), Instant.EPOCH);
            r.approve(Instant.EPOCH);
            assertThat(r.getStatus()).isEqualTo(AbsenceStatus.APPROVED);
            r.requestCancellation(UUID.randomUUID(), Instant.EPOCH);
            r.cancel(Instant.EPOCH);
            assertThat(r.getStatus()).isEqualTo(AbsenceStatus.CANCELLED);
        }

        @Test
        void draftCannotBeApprovedDirectly() {
            AbsenceRequest r = draft();
            assertThatThrownBy(() -> r.approve(Instant.EPOCH)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("DRAFT cannot become APPROVED");
        }

        @Test
        void approvedCannotBeSubmittedAgain() {
            assertThat(AbsenceStatus.APPROVED.canTransitionTo(AbsenceStatus.SUBMITTED)).isFalse();
            assertThat(AbsenceStatus.REJECTED.canTransitionTo(AbsenceStatus.APPROVED)).isFalse();
        }

        @Test
        void recalculationUpdatesDaysInPlace() {
            AbsenceRequest r = draft();
            UUID firstDayId = r.getDays().getFirst().getId();
            r.edit(ANNUAL.getId(), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 2), DayParts.FULL, null, null, Instant.EPOCH);
            r.replaceDays(AbsenceDayCalculator.calculate(r.getStartDate(), r.getEndDate(), ANNUAL, FULL_TIME,
                    Set.of()));
            assertThat(r.getDays()).hasSize(2);
            assertThat(r.getDays().getFirst().getId()).isEqualTo(firstDayId);
        }

        @Test
        void endBeforeStartIsRejected() {
            assertThatThrownBy(() -> AbsenceRequest.draft(UUID.randomUUID(), ANNUAL.getId(), LocalDate.of(2027, 3, 5),
                    LocalDate.of(2027, 3, 1), DayParts.FULL, null, null, Instant.EPOCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("end date");
        }
    }

    @Nested
    class Entitlement {

        @Test
        void ledgerMovesDaysBetweenReservedAndUsed() {
            LeaveEntitlement e = new LeaveEntitlement(UUID.randomUUID(), 2027, ANNUAL.getId(), new BigDecimal("30"),
                    new BigDecimal("3"), BigDecimal.ZERO, null);
            assertThat(e.remainingDays()).isEqualByComparingTo("33");
            e.reserve(new BigDecimal("5"));
            assertThat(e.remainingDays()).isEqualByComparingTo("28");
            e.consumeReservation(new BigDecimal("5"));
            assertThat(e.getReservedDays()).isEqualByComparingTo("0");
            assertThat(e.getUsedDays()).isEqualByComparingTo("5");
            e.restore(new BigDecimal("5"));
            assertThat(e.remainingDays()).isEqualByComparingTo("33");
            assertThat(e.covers(new BigDecimal("34"))).isFalse();
        }
    }
}
