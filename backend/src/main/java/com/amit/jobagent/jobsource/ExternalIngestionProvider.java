package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobIngestionProvider;

public enum ExternalIngestionProvider {
    JSEARCH,
    JOBSPY,
    CUSTOM_WEBHOOK,
    CUSTOM_RECIPE;

    JobIngestionProvider jobProvider() { return JobIngestionProvider.valueOf(name()); }
}
