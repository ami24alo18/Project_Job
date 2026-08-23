package com.amit.jobagent.profile.version;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface CandidateProfileVersionRepository extends JpaRepository<CandidateProfileVersion,UUID>{Optional<CandidateProfileVersion>findFirstByProfileIdOrderByVersionNumberDesc(UUID profileId);Optional<CandidateProfileVersion>findByProfileIdAndVersionNumber(UUID profileId,int number);Optional<CandidateProfileVersion>findByProfileIdAndActiveTrue(UUID profileId);List<CandidateProfileVersion>findByProfileIdOrderByVersionNumberDesc(UUID profileId);}
