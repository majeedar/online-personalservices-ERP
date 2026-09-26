package edu.university.ops.organisation.application;

import edu.university.ops.organisation.OrganisationDirectory;
import edu.university.ops.organisation.domain.OrganisationUnit;
import edu.university.ops.organisation.domain.OrganisationUnitRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganisationService implements OrganisationDirectory {

    private final OrganisationUnitRepository units;

    OrganisationService(OrganisationUnitRepository units) {
        this.units = units;
    }

    @Override
    public Optional<OrganisationUnitSummary> findUnit(UUID id) {
        return units.findById(id).map(OrganisationService::toSummary);
    }

    @Override
    public Optional<OrganisationUnitSummary> findUnitByCode(String code) {
        return units.findByCode(code).filter(OrganisationUnit::isActive).map(OrganisationService::toSummary);
    }

    @Override
    public List<OrganisationUnitSummary> allUnits() {
        return units.findByActiveTrueOrderByCode().stream().map(OrganisationService::toSummary).toList();
    }

    public List<OrganisationUnitSummary> activeUnits() {
        return allUnits();
    }

    private static OrganisationUnitSummary toSummary(OrganisationUnit u) {
        return new OrganisationUnitSummary(u.getId(), u.getCode(), u.getName(), u.getType().name(), u.getParentId(),
                u.getCostCentre());
    }
}
