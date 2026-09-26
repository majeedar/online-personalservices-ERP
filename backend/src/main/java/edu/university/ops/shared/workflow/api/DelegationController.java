package edu.university.ops.shared.workflow.api;

import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.Delegation;
import edu.university.ops.shared.workflow.DelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manage one's own delegations (AGENT.md §18). */
@RestController
@RequestMapping("/api/v1/delegations")
@Tag(name = "Delegation")
class DelegationController {

    private final DelegationService delegations;
    private final PersonDirectory persons;
    private final Clock clock;

    DelegationController(DelegationService delegations, PersonDirectory persons, Clock clock) {
        this.delegations = delegations;
        this.persons = persons;
        this.clock = clock;
    }

    record CreateDelegationRequest(@NotNull UUID delegateId, @NotNull ApprovalType approvalType,
                                   @NotNull LocalDate validFrom, @NotNull LocalDate validTo) {
    }

    record DelegationResponse(UUID id, UUID delegatorId, String delegatorName, UUID delegateId, String delegateName,
                              ApprovalType approvalType, LocalDate validFrom, LocalDate validTo, boolean active,
                              boolean effectiveToday, boolean givenByMe) {
    }

    @GetMapping
    @Operation(summary = "Delegations given by or to the caller")
    List<DelegationResponse> list() {
        OpsPrincipal me = CurrentUser.require();
        List<Delegation> list = delegations.involving(me.employeeId());
        return toResponses(list, me);
    }

    @PostMapping
    @Operation(summary = "Delegate one's approvals of a type for a period")
    DelegationResponse create(@Valid @RequestBody CreateDelegationRequest body) {
        OpsPrincipal me = CurrentUser.require();
        Delegation d = delegations.create(me, body.delegateId(), body.approvalType(), body.validFrom(),
                body.validTo());
        return toResponses(List.of(d), me).getFirst();
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke a delegation")
    ResponseEntity<Void> revoke(@PathVariable UUID id) {
        delegations.revoke(id, CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    private List<DelegationResponse> toResponses(List<Delegation> list, OpsPrincipal me) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(d -> {
            ids.add(d.getDelegatorId());
            ids.add(d.getDelegateId());
        });
        Map<UUID, String> names = persons.displayNames(ids);
        LocalDate today = LocalDate.now(clock);
        return list.stream().map(d -> new DelegationResponse(d.getId(), d.getDelegatorId(),
                names.get(d.getDelegatorId()), d.getDelegateId(), names.get(d.getDelegateId()), d.getApprovalType(),
                d.getValidFrom(), d.getValidTo(), d.isActive(), d.isEffectiveOn(today),
                d.getDelegatorId().equals(me.employeeId()))).toList();
    }
}
