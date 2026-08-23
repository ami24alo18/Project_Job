package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

interface JobSourceConfigurationRepository extends JpaRepository<JobSourceConfiguration,UUID>{
    List<JobSourceConfiguration>findAllByOrderByCreatedAtDescIdAsc();
    List<JobSourceConfiguration>findByEnabledTrueAndArchivedAtIsNullAndSourceTypeInOrderByCreatedAtAscIdAsc(List<JobSourceType>types);
    Optional<JobSourceConfiguration>findBySourceTypeAndProviderIdentifierIgnoreCaseAndArchivedAtIsNull(JobSourceType type,String identifier);
    boolean existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(JobSourceType type,String identifier,com.amit.jobagent.jobsource.connector.SourceRegion region);
    @Lock(LockModeType.PESSIMISTIC_WRITE)Optional<JobSourceConfiguration>findForUpdateById(UUID id);
}
