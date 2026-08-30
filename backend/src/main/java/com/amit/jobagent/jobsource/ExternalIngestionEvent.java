package com.amit.jobagent.jobsource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "external_ingestion_event")
class ExternalIngestionEvent {
    @Id private UUID id;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "event_id", nullable = false, length = 300) private String eventId;
    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_provider", nullable = false, length = 40)
    private ExternalIngestionProvider ingestionProvider;
    @Column(name = "search_rule_id") private UUID searchRuleId;
    @Column(name = "discovery_query", length = 500) private String discoveryQuery;
    @Column(name = "fetched_at", nullable = false) private Instant fetchedAt;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String payloadChecksum;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private ExternalIngestionEventStatus status;
    @Column(name = "discovered_count", nullable = false) private int discoveredCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "updated_count", nullable = false) private int updatedCount;
    @Column(name = "unchanged_count", nullable = false) private int unchangedCount;
    @Column(name = "duplicate_count", nullable = false) private int duplicateCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected ExternalIngestionEvent() {}

    ExternalIngestionEvent(
            UUID sourceId, String eventId, ExternalIngestionProvider ingestionProvider, UUID searchRuleId,
            String discoveryQuery, Instant fetchedAt, String payloadChecksum, int discoveredCount, Instant now) {
        id = UUID.randomUUID();
        this.sourceId = sourceId;
        this.eventId = eventId;
        this.ingestionProvider = ingestionProvider;
        this.searchRuleId = searchRuleId;
        this.discoveryQuery = discoveryQuery;
        this.fetchedAt = fetchedAt;
        this.payloadChecksum = payloadChecksum;
        this.discoveredCount = discoveredCount;
        status = ExternalIngestionEventStatus.PROCESSING;
        createdAt = now;
    }

    void complete(ExternalIngestionEventStatus status, RunMetrics metrics, Instant now) {
        this.status = status;
        discoveredCount = metrics.discovered();
        createdCount = metrics.created();
        updatedCount = metrics.updated();
        unchangedCount = metrics.unchanged();
        duplicateCount = metrics.duplicates();
        failedCount = metrics.failed();
        completedAt = now;
    }

    UUID id() { return id; }
    UUID sourceId() { return sourceId; }
    String eventId() { return eventId; }
    ExternalIngestionProvider ingestionProvider() { return ingestionProvider; }
    UUID searchRuleId() { return searchRuleId; }
    String discoveryQuery() { return discoveryQuery; }
    Instant fetchedAt() { return fetchedAt; }
    String payloadChecksum() { return payloadChecksum; }
    ExternalIngestionEventStatus status() { return status; }
    int discoveredCount() { return discoveredCount; }
    int createdCount() { return createdCount; }
    int updatedCount() { return updatedCount; }
    int unchangedCount() { return unchangedCount; }
    int duplicateCount() { return duplicateCount; }
    int failedCount() { return failedCount; }
    Instant completedAt() { return completedAt; }
}
