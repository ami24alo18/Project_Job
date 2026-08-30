package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.util.UUID;

public record JobSourceRunContext(
        UUID runId, UUID sourceId, String displayName, JobSourceType sourceType,
        JobSourceConnectorType connectorType, String providerIdentifier, SourceRegion region,
        String careerSiteUrl, String canonicalHost, int pageSize, int maximumPages,
        int missingRunThreshold, JobSourceRunCoverage coverage) {
    public JobSourceRunContext(UUID runId,UUID sourceId,String displayName,JobSourceType sourceType,String providerIdentifier,SourceRegion region,int pageSize,int maximumPages,int missingRunThreshold){
        this(runId,sourceId,displayName,sourceType,JobSourceConnectorType.defaultFor(sourceType),providerIdentifier,region,null,null,pageSize,maximumPages,missingRunThreshold,JobSourceRunCoverage.COMPLETE_INVENTORY);
    }
    public JobSourceRunContext(UUID runId,UUID sourceId,String displayName,JobSourceType sourceType,String providerIdentifier,SourceRegion region,int pageSize,int maximumPages,int missingRunThreshold,JobSourceRunCoverage coverage){
        this(runId,sourceId,displayName,sourceType,JobSourceConnectorType.defaultFor(sourceType),providerIdentifier,region,null,null,pageSize,maximumPages,missingRunThreshold,coverage);
    }
}
