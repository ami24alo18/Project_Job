package com.amit.jobagent.jobsource;

import java.util.List;
import java.util.UUID;

public record ExternalIngestionEventResponse(
        String eventId,
        UUID runId,
        boolean replayed,
        ExternalIngestionEventStatus status,
        int discoveredCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int duplicateCount,
        int failedCount,
        List<ExternalIngestionResultResponse> results) {}
