package com.amit.jobagent.jobsource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity@Table(name="job_source_run")
class JobSourceRun{
    @Id private UUID id;
    @Column(name="source_id",nullable=false)private UUID sourceId;
    @Enumerated(EnumType.STRING)@Column(name="trigger_type",nullable=false,length=20)private JobSourceTriggerType triggerType;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=30)private JobSourceRunCoverage coverage;
    @Column(name="search_rule_id")private UUID searchRuleId;
    @Column(name="external_event_id")private UUID externalEventId;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=30)private JobSourceRunStatus status;
    @Column(columnDefinition="text")private String checkpoint;
    @Column(name="started_at")private Instant startedAt;
    @Column(name="completed_at")private Instant completedAt;
    @Column(name="discovered_count",nullable=false)private int discoveredCount;
    @Column(name="created_count",nullable=false)private int createdCount;
    @Column(name="updated_count",nullable=false)private int updatedCount;
    @Column(name="unchanged_count",nullable=false)private int unchangedCount;
    @Column(name="duplicate_count",nullable=false)private int duplicateCount;
    @Column(name="failed_count",nullable=false)private int failedCount;
    @Column(name="removed_count",nullable=false)private int removedCount;
    @Column(name="safe_error_code",length=80)private String safeErrorCode;
    @Column(name="safe_error_message",length=500)private String safeErrorMessage;
    @Column(name="created_at",nullable=false,updatable=false)private Instant createdAt;
    protected JobSourceRun(){}
    JobSourceRun(UUID sourceId,JobSourceTriggerType triggerType,Instant now){this(sourceId,triggerType,JobSourceRunCoverage.COMPLETE_INVENTORY,null,null,now);}
    JobSourceRun(UUID sourceId,JobSourceTriggerType triggerType,JobSourceRunCoverage coverage,UUID searchRuleId,UUID externalEventId,Instant now){id=UUID.randomUUID();this.sourceId=sourceId;this.triggerType=triggerType;this.coverage=coverage;this.searchRuleId=searchRuleId;this.externalEventId=externalEventId;status=JobSourceRunStatus.QUEUED;createdAt=now;}
    boolean start(Instant now){if(status!=JobSourceRunStatus.QUEUED)return false;status=JobSourceRunStatus.RUNNING;startedAt=now;return true;}
    boolean complete(JobSourceRunStatus finalStatus,String checkpoint,RunMetrics metrics,Instant now,String errorCode,String errorMessage){if(status!=JobSourceRunStatus.RUNNING)return false;status=finalStatus;this.checkpoint=checkpoint;completedAt=now;discoveredCount=metrics.discovered();createdCount=metrics.created();updatedCount=metrics.updated();unchangedCount=metrics.unchanged();duplicateCount=metrics.duplicates();failedCount=metrics.failed();removedCount=metrics.removed();safeErrorCode=limit(errorCode,80);safeErrorMessage=limit(errorMessage,500);return true;}
    void fail(String code,String message,Instant now){status=JobSourceRunStatus.FAILED;completedAt=now;safeErrorCode=limit(code,80);safeErrorMessage=limit(message,500);}
    private static String limit(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
    UUID id(){return id;}UUID sourceId(){return sourceId;}JobSourceTriggerType triggerType(){return triggerType;}JobSourceRunCoverage coverage(){return coverage;}UUID searchRuleId(){return searchRuleId;}UUID externalEventId(){return externalEventId;}JobSourceRunStatus status(){return status;}String checkpoint(){return checkpoint;}Instant startedAt(){return startedAt;}Instant completedAt(){return completedAt;}int discoveredCount(){return discoveredCount;}int createdCount(){return createdCount;}int updatedCount(){return updatedCount;}int unchangedCount(){return unchangedCount;}int duplicateCount(){return duplicateCount;}int failedCount(){return failedCount;}int removedCount(){return removedCount;}String safeErrorCode(){return safeErrorCode;}String safeErrorMessage(){return safeErrorMessage;}Instant createdAt(){return createdAt;}
}
