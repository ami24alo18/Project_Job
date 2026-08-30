package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobSourceType;

public enum JobSourceConnectorType {
    LEVER,
    GREENHOUSE,
    EMAIL,
    JSEARCH,
    JOBSPY,
    CUSTOM_WEBHOOK,
    ORACLE_CX,
    WORKDAY,
    SMARTRECRUITERS,
    GENERIC_JSON_LD,
    CUSTOM_RECIPE;

    public static JobSourceConnectorType defaultFor(JobSourceType sourceType) {
        return switch (sourceType) {
            case LEVER -> LEVER;
            case GREENHOUSE -> GREENHOUSE;
            case EMAIL_WEBHOOK -> EMAIL;
            case EXTERNAL_API, CAREER_SITE, MANUAL -> null;
        };
    }
}
