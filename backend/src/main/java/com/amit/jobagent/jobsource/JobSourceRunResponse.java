package com.amit.jobagent.jobsource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSourceRunResponse(
        UUID id,
        UUID sourceId,
        JobSourceTriggerType triggerType,
        JobSourceRunCoverage coverage,
        UUID searchRuleId,
        UUID externalEventId,
        JobSourceRunStatus status,
        String checkpoint,
        Instant startedAt,
        Instant completedAt,
        int discoveredCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int duplicateCount,
        int failedCount,
        int removedCount,
        String safeErrorCode,
        String safeErrorMessage,
        Instant createdAt,
        List<JobSourceRunErrorResponse> errors) {

    public JobSourceRunResponse(
            UUID id, UUID sourceId, JobSourceTriggerType triggerType, JobSourceRunStatus status,
            String checkpoint, Instant startedAt, Instant completedAt, int discoveredCount,
            int createdCount, int updatedCount, int unchangedCount, int duplicateCount,
            int failedCount, int removedCount, String safeErrorCode, String safeErrorMessage,
            Instant createdAt, List<JobSourceRunErrorResponse> errors) {
        this(id, sourceId, triggerType, JobSourceRunCoverage.COMPLETE_INVENTORY, null, null, status,
                checkpoint, startedAt, completedAt, discoveredCount, createdCount, updatedCount,
                unchangedCount, duplicateCount, failedCount, removedCount, safeErrorCode,
                safeErrorMessage, createdAt, errors);
    }
}
