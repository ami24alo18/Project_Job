package com.amit.jobagent.jobsource;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface JobSourceRunErrorRepository extends JpaRepository<JobSourceRunError,UUID>{List<JobSourceRunError>findByRunIdOrderByCreatedAtAscIdAsc(UUID runId);}
