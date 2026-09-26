package edu.university.ops.organisation.domain;

import java.time.Instant;
import java.util.List;

/** Port to the personnel ERP's organisational structure (AGENT.md §22). */
public interface OrganisationGateway {

    List<ExternalOrganisationUnit> findChangedOrganisationUnits(Instant since);

    /** Unvalidated organisational unit as delivered by the ERP. */
    record ExternalOrganisationUnit(String orgCode, String externalId, String name, String type, String parentOrgCode,
                                    String costCentre, boolean active, Instant changedAt) {
    }
}
