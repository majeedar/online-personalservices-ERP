package edu.university.ops.travel.persistence;

import edu.university.ops.travel.domain.TravelPorts;
import edu.university.ops.travel.domain.TravelRequest;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaTravelRequestRepository extends Repository<TravelRequest, UUID>, TravelPorts.TravelRequestRepository {
}
