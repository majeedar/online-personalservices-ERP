package edu.university.ops.organisation.application;

import edu.university.ops.organisation.domain.OrganisationGateway;
import edu.university.ops.organisation.domain.OrganisationGateway.ExternalOrganisationUnit;
import edu.university.ops.organisation.domain.OrganisationUnit;
import edu.university.ops.organisation.domain.OrganisationUnitRepository;
import edu.university.ops.organisation.domain.OrganisationUnitType;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.batch.BatchJob;
import edu.university.ops.shared.integration.ExternalSystemException;
import edu.university.ops.shared.integration.IntegrationMonitor;
import edu.university.ops.shared.integration.IntegrationRun;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Organisation synchronisation (AGENT.md §27.2): changed units are mapped,
 * validated and upserted by their stable ERP code. Parents are processed before
 * children; invalid records are reported, never written.
 */
@Component
class OrganisationSyncJob implements BatchJob {

    static final String INTERFACE = "PERSONNEL_ORGANISATION_SYNC";
    private static final Pattern COST_CENTRE = Pattern.compile("CC-[0-9]{4}");

    private final OrganisationGateway gateway;
    private final OrganisationUnitRepository units;
    private final IntegrationMonitor monitor;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    OrganisationSyncJob(OrganisationGateway gateway, OrganisationUnitRepository units, IntegrationMonitor monitor,
                        AuditService audit, TransactionTemplate tx, Clock clock) {
        this.gateway = gateway;
        this.units = units;
        this.monitor = monitor;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "organisation-sync";
    }

    @Override
    public String description() {
        return "Synchronise faculties, institutes, departments and cost centres from the personnel ERP";
    }

    @Override
    public String defaultCron() {
        return "0 30 1 * * *";
    }

    @Override
    public void run(Context context) {
        UUID runId = monitor.startRun(INTERFACE, IntegrationRun.Trigger.SCHEDULED);
        int read = 0;
        int written = 0;
        int failed = 0;
        try {
            List<ExternalOrganisationUnit> changed = new ArrayList<>(
                    gateway.findChangedOrganisationUnits(context.lastSuccessfulRun().orElse(Instant.EPOCH)));
            // Parents first: units whose parent is not in this batch come before those whose parent is.
            changed.sort(Comparator.comparing(u -> changed.stream().anyMatch(p -> p.orgCode().equals(u.parentOrgCode()))));
            for (ExternalOrganisationUnit external : changed) {
                read++;
                Optional<String> problem = validate(external);
                if (problem.isPresent()) {
                    failed++;
                    context.failure(external.orgCode(), "VALIDATION_FAILED", problem.get());
                    monitor.recordError(runId, external.orgCode(), "VALIDATION_FAILED", problem.get(), 0, null);
                    continue;
                }
                Boolean changedUnit = tx.execute(s -> upsert(external));
                written += Boolean.TRUE.equals(changedUnit) ? 1 : 0;
                context.success();
            }
        } catch (ExternalSystemException e) {
            failed++;
            context.failure(null, e.errorCode(), e.getMessage());
            monitor.recordError(runId, null, e.errorCode(), e.getMessage(), 0, null);
        } finally {
            monitor.finishRun(runId, read, written, failed);
        }
    }

    Optional<String> validate(ExternalOrganisationUnit u) {
        if (u.orgCode() == null || u.orgCode().isBlank()) {
            return Optional.of("Organisation code missing");
        }
        if (parseType(u.type()) == null) {
            return Optional.of("Unknown organisation type " + u.type());
        }
        if (u.parentOrgCode() != null && units.findByCode(u.parentOrgCode()).isEmpty()) {
            return Optional.of("Parent unit " + u.parentOrgCode() + " does not exist");
        }
        if (u.costCentre() != null && !COST_CENTRE.matcher(u.costCentre()).matches()) {
            return Optional.of("Invalid cost centre format " + u.costCentre());
        }
        return Optional.empty();
    }

    private boolean upsert(ExternalOrganisationUnit e) {
        Instant now = Instant.now(clock);
        UUID parentId = e.parentOrgCode() == null ? null
                : units.findByCode(e.parentOrgCode()).map(OrganisationUnit::getId).orElse(null);
        Optional<OrganisationUnit> existing = units.findByCode(e.orgCode());
        OrganisationUnit unit = existing.orElseGet(() -> OrganisationUnit.imported(e.externalId(), e.orgCode(), now));
        String before = existing.map(OrganisationUnit::getName).orElse(null);
        boolean changed = unit.apply(e.name(), parseType(e.type()), parentId, e.costCentre(), e.active(), now);
        units.save(unit);
        if (changed || existing.isEmpty()) {
            audit.record(existing.isEmpty() ? "ORGANISATION_IMPORTED" : "ORGANISATION_UPDATED", "OrganisationUnit",
                    unit.getId(), before == null ? null : Map.of("name", before),
                    Map.of("code", e.orgCode(), "name", e.name(), "active", e.active()));
        }
        return changed || existing.isEmpty();
    }

    private static OrganisationUnitType parseType(String type) {
        try {
            return type == null ? null : OrganisationUnitType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
