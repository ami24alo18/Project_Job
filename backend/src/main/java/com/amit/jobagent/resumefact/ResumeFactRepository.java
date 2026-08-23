package com.amit.jobagent.resumefact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
interface ResumeFactRepository extends JpaRepository<ResumeFact,UUID>,JpaSpecificationExecutor<ResumeFact>{List<ResumeFact> findByProfileId(UUID profileId);List<ResumeFact> findByProfileIdAndStatusOrderByCreatedAtAscIdAsc(UUID profileId,ResumeFactStatus status);}
