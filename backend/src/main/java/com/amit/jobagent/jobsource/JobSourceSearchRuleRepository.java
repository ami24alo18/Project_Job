package com.amit.jobagent.jobsource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobSourceSearchRuleRepository extends JpaRepository<JobSourceSearchRule, UUID> {
    List<JobSourceSearchRule> findBySourceIdOrderByCreatedAtAscIdAsc(UUID sourceId);
    Optional<JobSourceSearchRule> findByIdAndSourceId(UUID id, UUID sourceId);
    boolean existsBySourceIdAndNameIgnoreCase(UUID sourceId, String name);
}
