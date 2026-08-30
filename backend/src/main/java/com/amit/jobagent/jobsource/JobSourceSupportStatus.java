package com.amit.jobagent.jobsource;

public enum JobSourceSupportStatus {
    SUPPORTED,
    NEEDS_AUTHORIZATION,
    NEEDS_ADAPTER,
    NEEDS_EXTRACTION_RECIPE,
    UNSUPPORTED,
    VALIDATION_FAILED
}
