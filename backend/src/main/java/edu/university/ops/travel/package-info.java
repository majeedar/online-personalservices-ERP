/**
 * Dienstreisemanagement: business travel requests, approvals, expenses and
 * settlement (AGENT.md §14).
 *
 * <p>Public API: the events in {@link edu.university.ops.travel.TravelEvents} and
 * {@link edu.university.ops.travel.TravelReports}. Subpackages are internal. The
 * external ports ({@code FinanceGateway}, {@code TravelErpGateway}) are owned here
 * and implemented in {@code travel.integration}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Travel",
        allowedDependencies = {"shared", "employee"})
package edu.university.ops.travel;
