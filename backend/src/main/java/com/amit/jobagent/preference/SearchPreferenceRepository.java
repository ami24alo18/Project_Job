package com.amit.jobagent.preference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface SearchPreferenceRepository extends JpaRepository<SearchPreference,UUID>{Optional<SearchPreference> findByProfileId(UUID profileId);}
