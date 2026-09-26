package edu.university.ops.shared.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port through which security resolves an authenticated identity to an active
 * employee and their currently valid roles. Implemented by the employee module,
 * so that {@code shared} never depends on a business module.
 */
public interface UserAccountLookup {

    Optional<UserAccount> findActiveAccount(String username);

    record UserAccount(UUID employeeId, String username, String displayName, Set<Role> roles) {
    }
}
