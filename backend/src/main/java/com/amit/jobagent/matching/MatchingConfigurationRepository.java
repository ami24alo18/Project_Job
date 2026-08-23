package com.amit.jobagent.matching;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface MatchingConfigurationRepository extends JpaRepository<MatchingConfiguration,UUID>{Optional<MatchingConfiguration>findByProfileId(UUID profileId);}
