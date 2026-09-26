package edu.university.ops.shared.batch;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

// Spring Data repositories of the batch infrastructure (package-private).

interface BatchJobRunRepository extends Repository<BatchJobRun, UUID> {
    BatchJobRun save(BatchJobRun run);

    BatchJobRun saveAndFlush(BatchJobRun run);

    Optional<BatchJobRun> findById(UUID id);

    Page<BatchJobRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<BatchJobRun> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

    Optional<BatchJobRun> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    Optional<BatchJobRun> findFirstByJobNameAndStatusInOrderByStartedAtDesc(String jobName,
                                                                          List<BatchJobRun.Status> statuses);

    List<BatchJobRun> findByStatus(BatchJobRun.Status status);

    long countByStatusAndStartedAtAfter(BatchJobRun.Status status, Instant after);
}

interface BatchJobErrorRepository extends Repository<BatchJobError, UUID> {
    BatchJobError save(BatchJobError error);

    List<BatchJobError> findByBatchJobRunId(UUID runId);
}
