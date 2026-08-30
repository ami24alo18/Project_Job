package com.amit.jobagent.common.error;

import org.springframework.http.HttpStatus;

/** A bounded, user-safe failure raised while inspecting a remote career site. */
public final class CareerSiteDiscoveryException extends RuntimeException {
    private final String safeCode;
    private final HttpStatus status;

    public CareerSiteDiscoveryException(HttpStatus status, String safeCode, String message) {
        super(message);
        this.status = status;
        this.safeCode = safeCode;
    }

    public CareerSiteDiscoveryException(HttpStatus status, String safeCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.safeCode = safeCode;
    }

    public String getSafeCode() { return safeCode; }
    public HttpStatus getStatus() { return status; }
}
