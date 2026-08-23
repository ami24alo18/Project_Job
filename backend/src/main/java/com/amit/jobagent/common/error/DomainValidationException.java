package com.amit.jobagent.common.error;
import java.util.Map;
public class DomainValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;
    public DomainValidationException(String message) { this(message, Map.of()); }
    public DomainValidationException(String message, Map<String, String> fieldErrors) { super(message); this.fieldErrors = Map.copyOf(fieldErrors); }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
