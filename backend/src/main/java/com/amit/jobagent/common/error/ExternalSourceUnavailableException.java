package com.amit.jobagent.common.error;

public class ExternalSourceUnavailableException extends RuntimeException {
    private final String safeCode;
    public ExternalSourceUnavailableException(String safeCode, String message) { super(message); this.safeCode = safeCode; }
    public ExternalSourceUnavailableException(String safeCode, String message, Throwable cause) { super(message, cause); this.safeCode = safeCode; }
    public String getSafeCode() { return safeCode; }
}
