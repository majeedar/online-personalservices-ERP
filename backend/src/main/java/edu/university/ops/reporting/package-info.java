/**
 * Simple operational reports with CSV export (AGENT.md §81). A top-level module
 * rather than {@code shared/reporting}: reports read from several business
 * modules, and {@code shared} must not depend on them (ADR-001). It uses only the
 * modules' public read APIs.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reporting",
        allowedDependencies = {"shared", "employee", "organisation", "absence", "travel", "time"})
package edu.university.ops.reporting;
