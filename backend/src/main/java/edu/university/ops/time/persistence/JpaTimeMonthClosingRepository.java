package edu.university.ops.time.persistence;

import edu.university.ops.time.domain.TimeMonthClosing;
import edu.university.ops.time.domain.TimeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaTimeMonthClosingRepository
        extends Repository<TimeMonthClosing, UUID>, TimeRepositories.TimeMonthClosingRepository {
}
