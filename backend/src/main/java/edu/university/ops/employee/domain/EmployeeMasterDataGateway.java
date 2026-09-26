package edu.university.ops.employee.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port to the personnel ERP, the system of record for employees (AGENT.md §22).
 * {@link ExternalEmployee} mirrors the ERP's record structure; it is converted to
 * internal data only by {@code PersonnelEmployeeMapper} (AGENT.md §24).
 */
public interface EmployeeMasterDataGateway {

    Optional<ExternalEmployee> findEmployee(String personnelNumber);

    List<ExternalEmployee> findChangedEmployees(Instant since);

    /**
     * One personnel record as delivered by the ERP (PERS_NR, ORG_CODE, EMP_PERCENT,
     * SUPERVISOR_REF, ...). Values are unvalidated.
     *
     * @param workDays weekday codes, e.g. "MO,TU,WE,TH"
     */
    record ExternalEmployee(String persNr, String firstName, String lastName, String email, String userId,
                            String orgCode, BigDecimal empPercent, String contractId, LocalDate contractStart,
                            LocalDate contractEnd, String contractType, String workDays, String supervisorRef,
                            boolean active, Instant changedAt) {
    }
}
