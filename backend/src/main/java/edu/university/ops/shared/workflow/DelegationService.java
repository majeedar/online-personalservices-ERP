package edu.university.ops.shared.workflow;

import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, revokes and resolves delegations (AGENT.md §18). */
@Service
@Transactional
public class DelegationService {

    static final int MAX_DAYS = 366;

    private final DelegationRepository delegations;
    private final PersonDirectory persons;
    private final AuditService audit;
    private final Clock clock;

    DelegationService(DelegationRepository delegations, PersonDirectory persons, AuditService audit, Clock clock) {
        this.delegations = delegations;
        this.persons = persons;
        this.audit = audit;
        this.clock = clock;
    }

    public Delegation create(OpsPrincipal delegator, UUID delegateId, ApprovalType type, LocalDate from,
                             LocalDate to) {
        if (delegator.employeeId().equals(delegateId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You cannot delegate to yourself.");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end date must not be before the start date.");
        }
        if (to.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A delegation cannot end in the past.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A delegation may last at most one year.");
        }
        boolean delegateActive = persons.findPerson(delegateId).map(PersonDirectory.Person::active).orElse(false);
        if (!delegateActive) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND, "The delegate is not an active employee.");
        }
        Delegation delegation = delegations.save(
                new Delegation(delegator.employeeId(), delegateId, type, from, to, Instant.now(clock)));
        audit.record("DELEGATION_CREATED", "Delegation", delegation.getId(), null,
                Map.of("delegateId", delegateId, "approvalType", type, "validFrom", from, "validTo", to));
        return delegation;
    }

    public void revoke(UUID delegationId, OpsPrincipal actor) {
        Delegation delegation = delegations.findById(delegationId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Delegation"));
        if (!delegation.getDelegatorId().equals(actor.employeeId()) && !actor.hasRole(Role.HR_ADMIN)) {
            throw BusinessException.forbidden();
        }
        delegation.revoke();
        audit.record("DELEGATION_REVOKED", "Delegation", delegation.getId(), Map.of("active", true),
                Map.of("active", false));
    }

    @Transactional(readOnly = true)
    public List<Delegation> involving(UUID employeeId) {
        return delegations.findByDelegatorIdOrDelegateIdOrderByValidFromDesc(employeeId, employeeId);
    }

    /** Delegators whose approvals of {@code type} the given delegate may currently perform. */
    @Transactional(readOnly = true)
    public List<Delegation> effectiveFor(UUID delegateId, LocalDate date) {
        return delegations.findByDelegateIdAndActiveTrue(delegateId).stream()
                .filter(d -> d.isEffectiveOn(date))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEffectiveDelegate(UUID delegatorId, UUID delegateId, ApprovalType type, LocalDate date) {
        return effectiveFor(delegateId, date).stream()
                .anyMatch(d -> d.getDelegatorId().equals(delegatorId) && d.getApprovalType() == type);
    }

    @Transactional(readOnly = true)
    public List<UUID> effectiveDelegatesOf(UUID delegatorId, ApprovalType type, LocalDate date) {
        return delegations.findByDelegatorIdAndActiveTrue(delegatorId).stream()
                .filter(d -> d.getApprovalType() == type && d.isEffectiveOn(date))
                .map(Delegation::getDelegateId)
                .toList();
    }
}
