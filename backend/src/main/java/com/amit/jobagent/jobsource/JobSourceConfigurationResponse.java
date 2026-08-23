package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.time.Instant;
import java.util.UUID;

public record JobSourceConfigurationResponse(UUID id,String displayName,JobSourceType sourceType,String providerIdentifier,SourceRegion region,boolean enabled,int pageSize,int maximumPagesPerRun,int missingRunThreshold,Instant lastSuccessfulSyncAt,Instant lastAttemptedSyncAt,int consecutiveFailureCount,long recordVersion,Instant createdAt,Instant updatedAt,Instant archivedAt){}
