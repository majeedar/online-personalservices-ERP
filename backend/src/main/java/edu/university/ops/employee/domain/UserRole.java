package edu.university.ops.employee.domain;

import edu.university.ops.shared.security.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Time-bounded role assignment (AGENT.md §10). */
@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    private UUID id;

    private UUID employeeId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id")
    private RoleDefinition role;

    private LocalDate validFrom;
    private LocalDate validTo;

    protected UserRole() {
    }

    public UserRole(UUID employeeId, RoleDefinition role, LocalDate validFrom) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.role = role;
        this.validFrom = validFrom;
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public Role role() {
        return role.getName();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
