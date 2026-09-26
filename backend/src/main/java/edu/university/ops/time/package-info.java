/**
 * Zeiterfassung: working-time recording, daily time accounts and corrections (AGENT.md §15).
 *
 * <p>Public API: {@link edu.university.ops.time.TimeAccounts}. Subpackages are internal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Time",
        allowedDependencies = {"shared", "employee", "calendar", "absence"})
package edu.university.ops.time;
