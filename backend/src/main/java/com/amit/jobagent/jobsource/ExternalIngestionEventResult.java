package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobIngestionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_ingestion_event_result")
class ExternalIngestionEventResult {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "request_index", nullable = false) private int requestIndex;
    @Column(name = "external_id", nullable = false, length = 300) private String externalId;
    @Column(name = "job_id") private UUID jobId;
    @Enumerated(EnumType.STRING) @Column(length = 30) private JobIngestionAction action;
    @Column(name = "safe_error_code", length = 80) private String safeErrorCode;
    @Column(name = "safe_error_message", length = 500) private String safeErrorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ExternalIngestionEventResult() {}

    static ExternalIngestionEventResult success(
            UUID eventId, int index, String externalId, UUID jobId, JobIngestionAction action, Instant now) {
        return new ExternalIngestionEventResult(eventId, index, externalId, jobId, action, null, null, now);
    }

    static ExternalIngestionEventResult failure(
            UUID eventId, int index, String externalId, String code, String message, Instant now) {
        return new ExternalIngestionEventResult(eventId, index, externalId, null, null, code, message, now);
    }

    private ExternalIngestionEventResult(
            UUID eventId, int index, String externalId, UUID jobId, JobIngestionAction action,
            String safeErrorCode, String safeErrorMessage, Instant now) {
        id = UUID.randomUUID();
        this.eventId = eventId;
        requestIndex = index;
        this.externalId = externalId;
        this.jobId = jobId;
        this.action = action;
        this.safeErrorCode = safeErrorCode;
        this.safeErrorMessage = safeErrorMessage;
        createdAt = now;
    }

    int requestIndex() { return requestIndex; }
    String externalId() { return externalId; }
    UUID jobId() { return jobId; }
    JobIngestionAction action() { return action; }
    String safeErrorCode() { return safeErrorCode; }
    String safeErrorMessage() { return safeErrorMessage; }
}
