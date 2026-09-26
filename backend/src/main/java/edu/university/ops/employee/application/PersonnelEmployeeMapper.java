package edu.university.ops.employee.application;

import edu.university.ops.employee.domain.EmployeeMasterDataGateway.ExternalEmployee;
import edu.university.ops.employee.domain.Employment;
import edu.university.ops.organisation.OrganisationDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Maps and validates personnel-ERP records (AGENT.md §24, §25). External field
 * semantics (PERS_NR, ORG_CODE, EMP_PERCENT, SUPERVISOR_REF, WORK_DAYS) end here;
 * invalid records produce problems and are never written.
 */
@Component
public class PersonnelEmployeeMapper {

    static final Pattern PERSONNEL_NUMBER = Pattern.compile("P[0-9]{5}");
    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[a-z]{2,}");
    static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9]{1,31}");
    static final int FULL_TIME_WEEKLY_MINUTES = 2400;
    static final Map<String, DayOfWeek> DAYS = Map.of("MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY, "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    private final OrganisationDirectory organisations;

    PersonnelEmployeeMapper(OrganisationDirectory organisations) {
        this.organisations = organisations;
    }

    /** Internal representation of one personnel record. */
    public record EmployeeImportData(String personnelNumber, String externalEmployeeId, String firstName,
                                     String lastName, String email, String username, UUID organisationUnitId,
                                     String contractId, LocalDate contractStart, LocalDate contractEnd,
                                     Employment.Type employmentType, BigDecimal fullTimeEquivalent,
                                     BigDecimal weeklyHours, Map<DayOfWeek, Integer> dailyTargets,
                                     String supervisorPersonnelNumber, boolean active) {
    }

    public record MappingResult(EmployeeImportData data, List<String> problems) {
        public boolean valid() {
            return problems.isEmpty();
        }
    }

    public MappingResult map(ExternalEmployee source) {
        List<String> problems = new ArrayList<>();
        if (source.persNr() == null || !PERSONNEL_NUMBER.matcher(source.persNr()).matches()) {
            problems.add("Personnel number missing or malformed: " + source.persNr());
        }
        if (blank(source.firstName()) || blank(source.lastName())) {
            problems.add("First and last name are required");
        }
        if (source.email() == null || !EMAIL.matcher(source.email()).matches()) {
            problems.add("Invalid e-mail address");
        }
        if (source.userId() == null || !USERNAME.matcher(source.userId()).matches()) {
            problems.add("Invalid user ID: " + source.userId());
        }
        UUID unitId = source.orgCode() == null ? null
                : organisations.findUnitByCode(source.orgCode()).map(OrganisationDirectory.OrganisationUnitSummary::id)
                .orElse(null);
        if (unitId == null) {
            problems.add("Organisation unit " + source.orgCode() + " does not exist");
        }
        BigDecimal percent = source.empPercent();
        if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            problems.add("Employment percentage must be between 0 and 100, was " + percent);
        }
        if (source.contractStart() == null) {
            problems.add("Contract start date missing");
        } else if (source.contractEnd() != null && source.contractEnd().isBefore(source.contractStart())) {
            problems.add("Contract ends before it starts");
        }
        if (blank(source.contractId())) {
            problems.add("Contract ID missing");
        }
        Employment.Type type = parseType(source.contractType());
        if (type == null) {
            problems.add("Unknown contract type " + source.contractType());
        }
        List<DayOfWeek> days = parseDays(source.workDays());
        if (days.isEmpty()) {
            problems.add("Invalid work days " + source.workDays());
        }
        if (source.supervisorRef() != null && source.supervisorRef().equals(source.persNr())) {
            problems.add("An employee cannot be their own supervisor");
        }
        if (!problems.isEmpty()) {
            return new MappingResult(null, problems);
        }

        BigDecimal fte = percent.divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        int weekly = fte.multiply(BigDecimal.valueOf(FULL_TIME_WEEKLY_MINUTES)).intValue();
        Map<DayOfWeek, Integer> targets = new EnumMap<>(DayOfWeek.class);
        days.forEach(d -> targets.put(d, weekly / days.size()));
        return new MappingResult(new EmployeeImportData(source.persNr(), "HR-" + source.persNr().substring(1),
                source.firstName().strip(), source.lastName().strip(), source.email().strip().toLowerCase(),
                source.userId(), unitId, source.contractId(), source.contractStart(), source.contractEnd(), type, fte,
                fte.multiply(BigDecimal.valueOf(40)).setScale(2, RoundingMode.HALF_UP), targets,
                blank(source.supervisorRef()) ? null : source.supervisorRef(), source.active()), List.of());
    }

    static List<DayOfWeek> parseDays(String codes) {
        if (blank(codes)) {
            return List.of();
        }
        List<DayOfWeek> days = new ArrayList<>();
        for (String code : codes.split(",")) {
            DayOfWeek day = DAYS.get(code.strip().toUpperCase());
            if (day == null) {
                return List.of();
            }
            days.add(day);
        }
        return days;
    }

    private static Employment.Type parseType(String type) {
        try {
            return type == null ? null : Employment.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
