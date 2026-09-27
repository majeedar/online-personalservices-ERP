package edu.university.ops.shared.directory;

import edu.university.ops.shared.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port through which shared platform services (workflow, notification) look up
 * minimal person data: name, email and whether the person is active. Implemented
 * by the employee module, so that {@code shared} never depends on a business module.
 */
public interface PersonDirectory {

    Optional<Person> findPerson(UUID employeeId);

    /** Display names for the given IDs; unknown IDs are absent from the map. */
    Map<UUID, String> displayNames(Collection<UUID> employeeIds);

    /** Active employees currently holding the role, e.g. to alert ERP admins. */
    List<UUID> activeEmployeesWithRole(Role role);

    /** @param language preferred language ("en" / "de"), null if not chosen */
    record Person(UUID id, String displayName, String email, boolean active, String language) {
    }
}
