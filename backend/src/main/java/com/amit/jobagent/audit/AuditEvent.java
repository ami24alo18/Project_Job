package com.amit.jobagent.audit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="audit_event")
class AuditEvent {
    @Id UUID id;
    @Column(name="event_type",nullable=false) String eventType;
    @Column(name="aggregate_type",nullable=false) String aggregateType;
    @Column(name="aggregate_id",nullable=false) UUID aggregateId;
    @Column(nullable=false) String actor;
    @Column(nullable=false) Instant timestamp;
    @Column(name="safe_metadata",nullable=false,columnDefinition="text") String safeMetadata;
    protected AuditEvent() {}
    AuditEvent(AuditEventType type,String aggregateType,UUID aggregateId,String actor,String metadata) {
        id=UUID.randomUUID(); eventType=type.name(); this.aggregateType=aggregateType; this.aggregateId=aggregateId; this.actor=actor; timestamp=Instant.now(); safeMetadata=metadata;
    }
}
