package com.amit.jobagent.job;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface JobPostingRepository extends JpaRepository<JobPosting,UUID>,JpaSpecificationExecutor<JobPosting>{
    Optional<JobPosting> findBySourceIdAndExternalId(UUID sourceId,String externalId);
    Optional<JobPosting> findByManualIdempotencyKey(String key);
    Optional<JobPosting> findFirstByCanonicalApplyUrlAndStatusInOrderByFirstSeenAtAscIdAsc(String url,Collection<JobPostingStatus> statuses);
    Optional<JobPosting> findFirstByFingerprintAndStatusInOrderByFirstSeenAtAscIdAsc(String fingerprint,Collection<JobPostingStatus> statuses);
    List<JobPosting> findBySourceId(UUID sourceId);
    List<JobPosting> findByDuplicateOfJobIdOrderByFirstSeenAtAscIdAsc(UUID jobId);
    long countByStatus(JobPostingStatus status);
}
