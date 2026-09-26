package edu.university.ops.organisation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port; implemented in {@code organisation.persistence}. */
public interface OrganisationUnitRepository {

    Optional<OrganisationUnit> findById(UUID id);

    Optional<OrganisationUnit> findByCode(String code);

    List<OrganisationUnit> findByActiveTrueOrderByCode();

    List<OrganisationUnit> findAll();

    OrganisationUnit save(OrganisationUnit unit);
}
