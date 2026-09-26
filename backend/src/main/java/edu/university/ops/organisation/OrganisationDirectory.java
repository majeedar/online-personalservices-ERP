package edu.university.ops.organisation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the organisation module. Other modules use this facade and
 * never access organisation tables or entities directly (ADR-003).
 */
public interface OrganisationDirectory {

    Optional<OrganisationUnitSummary> findUnit(UUID id);

    Optional<OrganisationUnitSummary> findUnitByCode(String code);

    List<OrganisationUnitSummary> allUnits();

    record OrganisationUnitSummary(UUID id, String code, String name, String type, UUID parentId,
                                   String costCentre) {
    }
}
