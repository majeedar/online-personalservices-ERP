package edu.university.ops.shared.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Spring Data repositories of the integration infrastructure (package-private).

interface IntegrationRunRepository extends Repository<IntegrationRun, UUID> {
    IntegrationRun save(IntegrationRun run);

    Optional<IntegrationRun> findById(UUID id);

    Page<IntegrationRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    long countByStatusAndStartedAtAfter(IntegrationRun.Status status, Instant after);
}

interface IntegrationErrorRepository extends Repository<IntegrationError, UUID> {
    IntegrationError save(IntegrationError error);

    Optional<IntegrationError> findById(UUID id);

    Page<IntegrationError> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<IntegrationError> findByResolvedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<IntegrationError> findByOutboxEventIdAndResolvedFalse(UUID outboxEventId);

    long countByResolvedFalse();
}

interface OutboxEventRepository extends Repository<OutboxEvent, UUID> {
    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID id);

    boolean existsByIdempotencyKey(String key);

    @Query("select e.id from OutboxEvent e where e.status = 'PENDING' and e.nextAttemptAt <= :now order by e.createdAt")
    List<UUID> findDueIds(@Param("now") Instant now);

    /** Row lock that skips events another processor is already delivering. */
    @Query(value = "select * from outbox_event where id = :id and status = 'PENDING' for update skip locked",
            nativeQuery = true)
    Optional<OutboxEvent> lockPending(@Param("id") UUID id);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(UUID aggregateId);

    long countByStatus(OutboxEvent.Status status);
}
