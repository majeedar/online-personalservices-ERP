package edu.university.ops.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local representation of an employee (AGENT.md §6.1). The personnel ERP is the
 * system of record; this copy is refreshed by synchronisation and deactivated,
 * never deleted.
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    private UUID id;

    private String externalEmployeeId;
    private String personnelNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String username;

    /** Reference by ID into the organisation module (ADR-003). */
    private UUID organisationUnitId;

    private UUID primaryEmploymentId;
    private boolean active;

    /** Interface and e-mail language ("en" / "de"); null until the employee chooses (ADR-020). */
    private String preferredLanguage;

    @Version
    private Long version;

    private Instant syncedAt;

    protected Employee() {
    }

    /** A new employee from the personnel ERP. */
    public static Employee imported(String externalId, String personnelNumber, String firstName, String lastName,
                                    String email, String username, UUID organisationUnitId, Instant now) {
        Employee e = new Employee();
        e.id = UUID.randomUUID();
        e.externalEmployeeId = externalId;
        e.personnelNumber = personnelNumber;
        e.username = username;
        e.active = true;
        e.applyMasterData(firstName, lastName, email, organisationUnitId, now);
        return e;
    }

    /** Applies master data from the personnel ERP; returns true if anything changed. */
    public boolean applyMasterData(String firstName, String lastName, String email, UUID organisationUnitId,
                                   Instant now) {
        boolean changed = !Objects.equals(this.firstName, firstName)
                || !Objects.equals(this.lastName, lastName)
                || !Objects.equals(this.email, email)
                || !Objects.equals(this.organisationUnitId, organisationUnitId);
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.organisationUnitId = organisationUnitId;
        this.syncedAt = now;
        return changed;
    }

    public void reactivate() {
        this.active = true;
    }

    public void usePrimaryEmployment(UUID employmentId) {
        this.primaryEmploymentId = employmentId;
    }

    public String displayName() {
        return firstName + " " + lastName;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalEmployeeId() {
        return externalEmployeeId;
    }

    public String getPersonnelNumber() {
        return personnelNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public UUID getOrganisationUnitId() {
        return organisationUnitId;
    }

    public UUID getPrimaryEmploymentId() {
        return primaryEmploymentId;
    }

    public boolean isActive() {
        return active;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void choosePreferredLanguage(String language) {
        if (!"en".equals(language) && !"de".equals(language)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        this.preferredLanguage = language;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
