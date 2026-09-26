package edu.university.ops.calendar.persistence;

import edu.university.ops.calendar.domain.Holiday;
import edu.university.ops.calendar.domain.HolidayRepository;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaHolidayRepository extends Repository<Holiday, UUID>, HolidayRepository {
}
