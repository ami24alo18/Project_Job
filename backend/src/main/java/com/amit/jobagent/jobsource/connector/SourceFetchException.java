package com.amit.jobagent.jobsource.connector;

import java.time.Duration;

/** Typed connector failure that never includes a response body or secret data. */
public final class SourceFetchException extends RuntimeException {
    private final SourceFetchErrorCode code;
    private final Integer httpStatus;
    private final boolean retryable;
    private final Duration retryAfter;

    public SourceFetchException(
            SourceFetchErrorCode code,
            String message,
            Integer httpStatus,
            boolean retryable,
            Duration retryAfter,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public SourceFetchException(SourceFetchErrorCode code, String message) {
        this(code, message, null, false, null, null);
    }

    public SourceFetchErrorCode code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
