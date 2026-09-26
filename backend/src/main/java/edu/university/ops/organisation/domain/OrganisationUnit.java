package edu.university.ops.organisation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A node in the university hierarchy (AGENT.md §7). The personnel ERP is the
 * system of record; units are deactivated, never deleted.
 */
@Entity
@Table(name = "organisation_unit")
public class OrganisationUnit {

    @Id
    private UUID id;

    private String externalId;
    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private OrganisationUnitType type;

    private UUID parentId;
    private String costCentre;
    private boolean active;

    @Version
    private Long version;

    private Instant syncedAt;

    protected OrganisationUnit() {
    }

    public static OrganisationUnit imported(String externalId, String code, Instant now) {
        OrganisationUnit u = new OrganisationUnit();
        u.id = UUID.randomUUID();
        u.externalId = externalId;
        u.code = code;
        u.syncedAt = now;
        return u;
    }

    /** Applies ERP data; returns true if anything changed. Units are deactivated, never deleted. */
    public boolean apply(String name, OrganisationUnitType type, UUID parentId, String costCentre, boolean active,
                         Instant now) {
        boolean changed = !java.util.Objects.equals(this.name, name) || this.type != type
                || !java.util.Objects.equals(this.parentId, parentId)
                || !java.util.Objects.equals(this.costCentre, costCentre) || this.active != active;
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.costCentre = costCentre;
        this.active = active;
        this.syncedAt = now;
        return changed;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public OrganisationUnitType getType() {
        return type;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCostCentre() {
        return costCentre;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
