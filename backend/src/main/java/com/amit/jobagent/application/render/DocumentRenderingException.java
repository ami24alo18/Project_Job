package com.amit.jobagent.application.render;

public final class DocumentRenderingException extends RuntimeException {
    DocumentRenderingException(String message) {
        super(message);
    }

    DocumentRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
