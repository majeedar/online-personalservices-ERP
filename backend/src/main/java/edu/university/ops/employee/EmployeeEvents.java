package edu.university.ops.employee;

import java.util.UUID;

/** Events of the employee module (AGENT.md §49). */
public final class EmployeeEvents {

    private EmployeeEvents() {
    }

    /** Master data of an employee changed through synchronisation with the personnel ERP. */
    public record EmployeeMasterDataChanged(UUID employeeId, boolean created, boolean deactivated) {
    }
}
