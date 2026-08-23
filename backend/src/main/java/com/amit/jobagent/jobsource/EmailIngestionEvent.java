package com.amit.jobagent.jobsource;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity@Table(name="email_ingestion_event")
class EmailIngestionEvent{
    @Id private UUID id;@Column(name="message_id",nullable=false,unique=true,length=300)private String messageId;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)private EmailProvider provider;@Column(name="source_id",nullable=false)private UUID sourceId;@Column(name="received_at",nullable=false)private Instant receivedAt;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)private EmailIngestionStatus status;@Column(name="job_count",nullable=false)private int jobCount;@Column(name="created_count",nullable=false)private int createdCount;@Column(name="updated_count",nullable=false)private int updatedCount;@Column(name="unchanged_count",nullable=false)private int unchangedCount;@Column(name="duplicate_count",nullable=false)private int duplicateCount;@Column(name="failed_count",nullable=false)private int failedCount;@Column(name="created_at",nullable=false,updatable=false)private Instant createdAt;
    protected EmailIngestionEvent(){}EmailIngestionEvent(String messageId,EmailProvider provider,UUID sourceId,Instant receivedAt,Instant now){id=UUID.randomUUID();this.messageId=messageId;this.provider=provider;this.sourceId=sourceId;this.receivedAt=receivedAt;status=EmailIngestionStatus.PROCESSING;createdAt=now;}
    void finish(int jobs,int created,int updated,int unchanged,int duplicates,int failed){jobCount=jobs;createdCount=created;updatedCount=updated;unchangedCount=unchanged;duplicateCount=duplicates;failedCount=failed;status=failed==0?EmailIngestionStatus.SUCCEEDED:EmailIngestionStatus.PARTIAL_SUCCESS;}
    UUID id(){return id;}String messageId(){return messageId;}EmailIngestionStatus status(){return status;}int jobCount(){return jobCount;}int createdCount(){return createdCount;}int updatedCount(){return updatedCount;}int unchangedCount(){return unchangedCount;}int duplicateCount(){return duplicateCount;}int failedCount(){return failedCount;}
}
