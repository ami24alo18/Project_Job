package com.amit.jobagent.jobsource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface JobSourceRunRepository extends JpaRepository<JobSourceRun,UUID>,JpaSpecificationExecutor<JobSourceRun>{
    boolean existsBySourceIdAndStatusIn(UUID sourceId,Collection<JobSourceRunStatus>statuses);
    List<JobSourceRun>findByStatusIn(Collection<JobSourceRunStatus>statuses);
    Optional<JobSourceRun>findFirstBySourceIdAndStatusInOrderByCreatedAtDescIdDesc(
            UUID sourceId,Collection<JobSourceRunStatus>statuses);
    Optional<JobSourceRun>findFirstBySourceIdAndIdNotOrderByCreatedAtDescIdDesc(UUID sourceId,UUID excludedRunId);
}
