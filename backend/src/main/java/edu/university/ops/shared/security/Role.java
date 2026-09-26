package edu.university.ops.shared.security;

/**
 * Role catalogue (AGENT.md §10). Mirrors the reference rows in the {@code role} table.
 * Delegation is a relation, not a role (ADR-011).
 */
public enum Role {
    EMPLOYEE,
    SUPERVISOR,
    HR_ADMIN,
    TRAVEL_OFFICE,
    FINANCIAL_APPROVER,
    TIME_ADMIN,
    ERP_ADMIN,
    SUPPORT,
    AUDITOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
