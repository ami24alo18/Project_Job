package com.amit.jobagent.job;

public enum JobIngestionProvider {
    LEVER,
    GREENHOUSE,
    EMAIL,
    MANUAL,
    JSEARCH,
    JOBSPY,
    CUSTOM_WEBHOOK,
    ORACLE_CX,
    WORKDAY,
    SMARTRECRUITERS,
    GENERIC_JSON_LD,
    CUSTOM_RECIPE;

    public static JobIngestionProvider defaultFor(JobSourceType sourceType) {
        return switch (sourceType) {
            case LEVER -> LEVER;
            case GREENHOUSE -> GREENHOUSE;
            case EMAIL_WEBHOOK -> EMAIL;
            case MANUAL -> MANUAL;
            case EXTERNAL_API, CAREER_SITE -> null;
        };
    }
}
