package com.amit.jobagent.jobsource;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExternalIngestionEventResultRepository extends JpaRepository<ExternalIngestionEventResult, UUID> {
    List<ExternalIngestionEventResult> findByEventIdOrderByRequestIndexAsc(UUID eventId);
}
