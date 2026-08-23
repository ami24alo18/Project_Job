package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.job.JobSourceType;

/** Read-only adapter for an authorized public job feed. */
public interface JobSourceConnector {
    JobSourceType supportedType();

    SourceFetchResult fetch(SourceFetchRequest request);
}
