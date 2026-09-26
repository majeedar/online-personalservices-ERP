package edu.university.ops.shared.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated user as seen by the application. Roles are resolved on the
 * server at login; role information sent by the frontend is never trusted.
 */
public record OpsPrincipal(UUID employeeId, String username, String displayName, Set<Role> roles)
        implements Serializable {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... candidates) {
        for (Role r : candidates) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
