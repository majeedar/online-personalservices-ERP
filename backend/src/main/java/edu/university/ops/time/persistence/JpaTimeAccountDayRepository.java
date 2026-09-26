package edu.university.ops.time.persistence;

import edu.university.ops.time.domain.TimeAccountDay;
import edu.university.ops.time.domain.TimeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaTimeAccountDayRepository extends Repository<TimeAccountDay, UUID>, TimeRepositories.TimeAccountDayRepository {
}
