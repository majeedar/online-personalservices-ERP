package edu.university.ops.shared.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Deliberately exposes no update or delete operations: audit records can only
 * be appended and read (AGENT.md §20). The database trigger is the backstop.
 */
public interface AuditLogRepository extends Repository<AuditLogEntry, UUID> {

    AuditLogEntry save(AuditLogEntry entry);

    Page<AuditLogEntry> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLogEntry> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId,
                                                                        Pageable pageable);
}
