package edu.university.ops.organisation.api;

import edu.university.ops.organisation.OrganisationDirectory.OrganisationUnitSummary;
import edu.university.ops.organisation.application.OrganisationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organisation-units")
@Tag(name = "Organisation")
class OrganisationController {

    private final OrganisationService organisationService;

    OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @GetMapping
    @Operation(summary = "Active organisational units as a flat list (build the tree via parentId)")
    List<OrganisationUnitSummary> list() {
        return organisationService.activeUnits();
    }
}
