/**
 * Abwesenheitsverwaltung — leave and absence management (AGENT.md §13).
 *
 * <p>Public API: {@link edu.university.ops.absence.AbsenceLookup} and the events in
 * {@link edu.university.ops.absence.AbsenceEvents}. Subpackages are internal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Absence",
        allowedDependencies = {"shared", "employee", "calendar"})
package edu.university.ops.absence;
