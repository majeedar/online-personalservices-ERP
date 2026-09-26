package edu.university.ops.organisation.persistence;

import edu.university.ops.organisation.domain.OrganisationUnit;
import edu.university.ops.organisation.domain.OrganisationUnitRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data adapter: implements the domain port's methods by derivation. */
public interface JpaOrganisationUnitRepository extends Repository<OrganisationUnit, UUID>, OrganisationUnitRepository {
}
