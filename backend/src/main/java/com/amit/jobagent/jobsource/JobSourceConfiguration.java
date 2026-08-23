package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity @Table(name="job_source_configuration")
class JobSourceConfiguration extends MutableEntity {
    @Column(name="display_name",nullable=false,length=200)private String displayName;
    @Enumerated(EnumType.STRING)@Column(name="source_type",nullable=false,length=30)private JobSourceType sourceType;
    @Column(name="provider_identifier",nullable=false,length=200)private String providerIdentifier;
    @Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private SourceRegion region;
    @Column(nullable=false)private boolean enabled;
    @Column(name="page_size",nullable=false)private int pageSize;
    @Column(name="maximum_pages_per_run",nullable=false)private int maximumPagesPerRun;
    @Column(name="missing_run_threshold",nullable=false)private int missingRunThreshold;
    @Column(name="last_successful_sync_at")private Instant lastSuccessfulSyncAt;
    @Column(name="last_attempted_sync_at")private Instant lastAttemptedSyncAt;
    @Column(name="consecutive_failure_count",nullable=false)private int consecutiveFailureCount;
    @Column(name="archived_at")private Instant archivedAt;
    protected JobSourceConfiguration(){}
    JobSourceConfiguration(JobSourceConfigurationRequest request){apply(request,true);}
    void apply(JobSourceConfigurationRequest request,boolean initial){if(archivedAt!=null)throw new DomainValidationException("Archived job sources cannot be edited");displayName=request.displayName().trim();sourceType=request.sourceType();providerIdentifier=request.providerIdentifier().trim();region=request.region();enabled=initial?Boolean.TRUE.equals(request.enabled()):Boolean.TRUE.equals(request.enabled());pageSize=request.pageSize();maximumPagesPerRun=request.maximumPagesPerRun();missingRunThreshold=request.missingRunThreshold();if(!initial)touch();}
    void enable(){if(archivedAt!=null)throw new DomainValidationException("Archived job sources cannot be enabled");enabled=true;touch();}
    void disable(){enabled=false;touch();}
    void archive(){enabled=false;archivedAt=Instant.now();touch();}
    void attempted(Instant at){lastAttemptedSyncAt=at;touch();}
    void succeeded(Instant at){lastSuccessfulSyncAt=at;consecutiveFailureCount=0;touch();}
    void failed(){consecutiveFailureCount++;touch();}
    String displayName(){return displayName;}JobSourceType sourceType(){return sourceType;}String providerIdentifier(){return providerIdentifier;}
    SourceRegion region(){return region;}boolean enabled(){return enabled;}int pageSize(){return pageSize;}int maximumPagesPerRun(){return maximumPagesPerRun;}
    int missingRunThreshold(){return missingRunThreshold;}Instant lastSuccessfulSyncAt(){return lastSuccessfulSyncAt;}Instant lastAttemptedSyncAt(){return lastAttemptedSyncAt;}
    int consecutiveFailureCount(){return consecutiveFailureCount;}Instant archivedAt(){return archivedAt;}
}
