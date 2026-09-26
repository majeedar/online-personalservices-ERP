package edu.university.ops.travel.persistence;

import edu.university.ops.travel.domain.FundingSource;
import edu.university.ops.travel.domain.TravelPorts;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaFundingSourceRepository extends Repository<FundingSource, UUID>, TravelPorts.FundingSourceRepository {
}
