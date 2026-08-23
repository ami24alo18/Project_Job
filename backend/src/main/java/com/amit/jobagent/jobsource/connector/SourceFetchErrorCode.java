package com.amit.jobagent.jobsource.connector;

/** Stable, safe failure categories suitable for source-run persistence. */
public enum SourceFetchErrorCode {
    INVALID_CONFIGURATION,
    UNSUPPORTED_SOURCE,
    ENDPOINT_NOT_ALLOWED,
    REDIRECT_REJECTED,
    PERMANENT_HTTP_ERROR,
    RATE_LIMITED,
    RETRYABLE_HTTP_ERROR,
    TIMEOUT,
    IO_ERROR,
    INTERRUPTED,
    RESPONSE_TOO_LARGE,
    INVALID_PAYLOAD
}
