package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;
import java.util.UUID;

record AuthenticatedExternalSource(
        UUID id,
        JobSourceType sourceType,
        JobSourceConnectorType connectorType,
        String displayName,
        String extractionRecipeVersion) {}
