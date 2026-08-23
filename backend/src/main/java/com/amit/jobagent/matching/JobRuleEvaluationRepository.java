package com.amit.jobagent.matching; import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface JobRuleEvaluationRepository extends JpaRepository<JobRuleEvaluation,UUID>{Optional<JobRuleEvaluation>findByJobIdAndProfileVersionIdAndRulesetVersionAndJobContentHashAndProfileSnapshotChecksum(UUID j,UUID p,String r,String jh,String ph);}
