package com.amit.jobagent.jobsource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CareerSiteJobSourceRequest(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 2000) String careerSiteUrl,
        Boolean enabled,
        @Min(1) @Max(100) int pageSize,
        @Min(1) @Max(100) int maximumPagesPerRun,
        @Min(1) @Max(20) int missingRunThreshold) {}
