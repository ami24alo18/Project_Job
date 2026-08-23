package com.amit.jobagent.application;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicationPackageRepository extends JpaRepository<ApplicationPackage,UUID>{
 Optional<ApplicationPackage>findByCandidateIdAndJobId(UUID candidate,UUID job);
 List<ApplicationPackage>findByCandidateIdOrderByCreatedAtDesc(UUID candidate);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from ApplicationPackage p where p.candidateId=:candidate and p.jobId=:job")
 Optional<ApplicationPackage>findForUpdate(@Param("candidate")UUID candidate,@Param("job")UUID job);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from ApplicationPackage p where p.id=:id")
 Optional<ApplicationPackage>findByIdForUpdate(@Param("id")UUID id);
}
