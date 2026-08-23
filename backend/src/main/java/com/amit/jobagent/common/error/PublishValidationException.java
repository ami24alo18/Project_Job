package com.amit.jobagent.common.error;
import java.util.Map;
public final class PublishValidationException extends DomainValidationException {
    public PublishValidationException(String message, Map<String, String> fieldErrors) { super(message, fieldErrors); }
}
