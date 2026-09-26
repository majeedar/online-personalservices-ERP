package edu.university.ops.time.persistence;

import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaTimeCorrectionRepository extends Repository<TimeCorrectionRequest, UUID>, TimeRepositories.TimeCorrectionRepository {
}
