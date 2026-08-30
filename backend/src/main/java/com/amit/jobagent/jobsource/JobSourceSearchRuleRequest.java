package com.amit.jobagent.jobsource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JobSourceSearchRuleRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 500) String query,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> locations,
        boolean remoteAllowed,
        boolean hybridAllowed,
        boolean onsiteAllowed,
        @NotNull DatePostedWindow datePostedWindow,
        @Min(1) @Max(1000) int maximumResults,
        Boolean enabled,
        Long recordVersion) {}
