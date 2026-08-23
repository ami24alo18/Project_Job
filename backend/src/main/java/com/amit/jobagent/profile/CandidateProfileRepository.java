package com.amit.jobagent.profile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
interface CandidateProfileRepository extends JpaRepository<CandidateProfile,UUID> {
    Optional<CandidateProfile> findFirstByOrderByCreatedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CandidateProfile p where p.id=:id")
    Optional<CandidateProfile> findByIdForUpdate(@Param("id") UUID id);
}
