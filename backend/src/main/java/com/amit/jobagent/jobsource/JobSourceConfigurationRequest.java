package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobSourceConfigurationRequest(
        @NotBlank@Size(max=200)String displayName,
        @NotNull JobSourceType sourceType,
        @NotBlank@Size(max=200)String providerIdentifier,
        @NotNull SourceRegion region,
        Boolean enabled,
        @Min(1)@Max(100)int pageSize,
        @Min(1)@Max(100)int maximumPagesPerRun,
        @Min(1)@Max(20)int missingRunThreshold,
        Long recordVersion){}
