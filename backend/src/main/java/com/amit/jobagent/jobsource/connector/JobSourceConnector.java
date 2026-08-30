package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.JobSourceConnectorType;

/** Read-only adapter for an authorized public job feed. */
public interface JobSourceConnector {
    JobSourceType supportedType();

    default JobSourceConnectorType supportedConnector() {
        return JobSourceConnectorType.defaultFor(supportedType());
    }

    SourceFetchResult fetch(SourceFetchRequest request);
}
