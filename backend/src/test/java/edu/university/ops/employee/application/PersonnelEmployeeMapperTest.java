package edu.university.ops.employee.application;

import static org.assertj.core.api.Assertions.assertThat;

import edu.university.ops.employee.domain.EmployeeMasterDataGateway.ExternalEmployee;
import edu.university.ops.employee.domain.Employment;
import edu.university.ops.organisation.OrganisationDirectory;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Mapping and validation of personnel-ERP records (AGENT.md §24, §25). */
class PersonnelEmployeeMapperTest {

    static final UUID UNIT = UUID.randomUUID();

    final PersonnelEmployeeMapper mapper = new PersonnelEmployeeMapper(new OrganisationDirectory() {
        public Optional<OrganisationUnitSummary> findUnit(UUID id) {
            return Optional.empty();
        }

        public Optional<OrganisationUnitSummary> findUnitByCode(String code) {
            return "INST-A1".equals(code)
                    ? Optional.of(new OrganisationUnitSummary(UNIT, code, "Institute", "INSTITUTE", null, "CC-2100"))
                    : Optional.empty();
        }

        public List<OrganisationUnitSummary> allUnits() {
            return List.of();
        }
    });

    static ExternalEmployee record(String persNr, String orgCode, String percent, String workDays) {
        return new ExternalEmployee(persNr, "Lena", "Neu", "Lena.Neu@uni.example", "lneu", orgCode,
                new BigDecimal(percent), "EMP-1", LocalDate.of(2026, 9, 1), null, "ACADEMIC", workDays, "P10002", true,
                Instant.EPOCH);
    }

    @Test
    void mapsExternalFieldsToTheInternalModel() {
        var result = mapper.map(record("P10019", "INST-A1", "80", "MO,TU,WE,TH"));

        assertThat(result.valid()).isTrue();
        var data = result.data();
        assertThat(data.organisationUnitId()).isEqualTo(UNIT);
        assertThat(data.email()).isEqualTo("lena.neu@uni.example");
        assertThat(data.fullTimeEquivalent()).isEqualByComparingTo("0.8");
        assertThat(data.weeklyHours()).isEqualByComparingTo("32");
        assertThat(data.employmentType()).isEqualTo(Employment.Type.ACADEMIC);
        // 80 % of 2400 minutes over four days: 480 per day; Friday is not a working day.
        assertThat(data.dailyTargets()).containsEntry(DayOfWeek.MONDAY, 480).doesNotContainKey(DayOfWeek.FRIDAY);
        assertThat(data.supervisorPersonnelNumber()).isEqualTo("P10002");
    }

    @Test
    void invalidRecordsAreRejectedWithReasons() {
        assertThat(mapper.map(record("P10019", "XX-UNKNOWN", "100", "MO")).problems())
                .anyMatch(p -> p.contains("does not exist"));
        assertThat(mapper.map(record("P10019", "INST-A1", "140", "MO")).problems())
                .anyMatch(p -> p.contains("between 0 and 100"));
        assertThat(mapper.map(record("10019", "INST-A1", "100", "MO")).problems())
                .anyMatch(p -> p.contains("Personnel number"));
        assertThat(mapper.map(record("P10019", "INST-A1", "100", "MO,XX")).problems())
                .anyMatch(p -> p.contains("work days"));
    }
}
