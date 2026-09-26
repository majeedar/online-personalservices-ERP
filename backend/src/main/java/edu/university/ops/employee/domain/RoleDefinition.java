package edu.university.ops.employee.domain;

import edu.university.ops.shared.security.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Row of the fixed role catalogue (table {@code role}). */
@Entity
@Table(name = "role")
public class RoleDefinition {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Role name;

    protected RoleDefinition() {
    }

    public UUID getId() {
        return id;
    }

    public Role getName() {
        return name;
    }
}
