package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.RawJobRecord;

public interface JobSourceNormalizer {
    JobSourceType supportedType();
    default JobSourceConnectorType supportedConnector() { return JobSourceConnectorType.defaultFor(supportedType()); }
    JobCandidate normalize(RawJobRecord raw, JobSourceRunContext source);
}
