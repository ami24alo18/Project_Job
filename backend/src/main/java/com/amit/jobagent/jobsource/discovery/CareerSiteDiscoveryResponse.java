package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.JobSourceSupportStatus;

public record CareerSiteDiscoveryResponse(
        String canonicalUrl,
        String canonicalHost,
        JobSourceConnectorType connectorType,
        String providerIdentifier,
        JobSourceSupportStatus supportStatus,
        String supportMessage,
        String detectionVersion) {}
