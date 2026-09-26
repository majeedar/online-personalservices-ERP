/**
 * Employee master data: employees, employments, work schedules, role
 * assignments and approval relations. A synchronised copy of the personnel
 * ERP, which stays the system of record (AGENT.md §6.1).
 *
 * <p>Public API: {@link edu.university.ops.employee.EmployeeDirectory} and
 * {@link edu.university.ops.employee.ApprovalType}. Subpackages are internal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Employee",
        allowedDependencies = {"shared", "organisation"})
package edu.university.ops.employee;
