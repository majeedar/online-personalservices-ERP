package edu.university.ops.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.shared.monitoring.CorrelationId;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit entries. Joins the caller's transaction, so an audit record is
 * committed if and only if the audited change is committed.
 *
 * <p>Do not pass free text or full personnel records as old/new values; audit
 * only the fields that changed (data protection, AGENT.md §82).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Audit an action performed by the currently authenticated user (or the system, if none). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Object entityId, Object oldValue, Object newValue) {
        Optional<OpsPrincipal> actor = CurrentUser.find();
        save(actor.map(OpsPrincipal::employeeId).orElse(null),
                actor.map(OpsPrincipal::username).orElse("system"),
                action, entityType, entityId, oldValue, newValue);
    }

    /** Audit an action where only a claimed username is known, e.g. a failed login. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAnonymous(String claimedUsername, String action, String entityType, Object entityId) {
        save(null, truncate(claimedUsername), action, entityType, entityId, null, null);
    }

    private void save(java.util.UUID actorId, String actorUsername, String action, String entityType,
                      Object entityId, Object oldValue, Object newValue) {
        repository.save(new AuditLogEntry(actorId, actorUsername, action, entityType,
                entityId == null ? null : entityId.toString(),
                toJson(oldValue), toJson(newValue), Instant.now(clock), CorrelationId.current()));
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 64 ? s : s.substring(0, 64);
    }
}
