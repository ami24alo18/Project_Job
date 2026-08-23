package com.amit.jobagent.common.error;

public final class InvalidWebhookSecretException extends RuntimeException {
    public InvalidWebhookSecretException() {
        super("The webhook secret is invalid");
    }
}
