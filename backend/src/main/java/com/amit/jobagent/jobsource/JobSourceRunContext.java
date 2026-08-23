package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.util.UUID;

public record JobSourceRunContext(UUID runId,UUID sourceId,String displayName,JobSourceType sourceType,String providerIdentifier,SourceRegion region,int pageSize,int maximumPages,int missingRunThreshold){}
