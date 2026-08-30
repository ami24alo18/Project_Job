package com.amit.jobagent.jobsource.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CareerSiteDiscoveryRequest(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 2000) String careerSiteUrl) {}
