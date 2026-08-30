package com.amit.jobagent.jobsource;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExternalIngestionEventRepository extends JpaRepository<ExternalIngestionEvent, UUID> {
    Optional<ExternalIngestionEvent> findBySourceIdAndEventId(UUID sourceId, String eventId);
}
