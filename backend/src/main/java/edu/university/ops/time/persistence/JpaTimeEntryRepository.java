package edu.university.ops.time.persistence;

import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.time.domain.TimeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaTimeEntryRepository extends Repository<TimeEntry, UUID>, TimeRepositories.TimeEntryRepository {
}
