package com.amit.jobagent.common.error;

public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(String message) {
        super(message);
    }
}
