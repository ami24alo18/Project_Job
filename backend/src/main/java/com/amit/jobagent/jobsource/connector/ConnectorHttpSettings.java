package com.amit.jobagent.jobsource.connector;

import java.time.Duration;
import java.util.Objects;

/** Bounded transport limits shared by all public-feed connectors. */
public record ConnectorHttpSettings(
        Duration connectTimeout,
        Duration responseTimeout,
        int maximumResponseBytes,
        int maximumAttempts,
        Duration initialBackoff,
        Duration maximumBackoff,
        String userAgent) {

    public ConnectorHttpSettings {
        connectTimeout = positive(connectTimeout, "connectTimeout");
        responseTimeout = positive(responseTimeout, "responseTimeout");
        initialBackoff = positive(initialBackoff, "initialBackoff");
        maximumBackoff = positive(maximumBackoff, "maximumBackoff");
        userAgent = Objects.requireNonNull(userAgent, "userAgent is required").trim();
        if (maximumResponseBytes < 1 || maximumResponseBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maximumResponseBytes must be between 1 and Integer.MAX_VALUE - 1");
        }
        if (maximumAttempts < 1 || maximumAttempts > 5) {
            throw new IllegalArgumentException("maximumAttempts must be between 1 and 5");
        }
        if (maximumBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maximumBackoff must not be shorter than initialBackoff");
        }
        if (userAgent.isEmpty()) {
            throw new IllegalArgumentException("userAgent is required");
        }
    }

    public static ConnectorHttpSettings defaults() {
        return new ConnectorHttpSettings(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                10 * 1024 * 1024,
                3,
                Duration.ofMillis(250),
                Duration.ofSeconds(10),
                "job-application-agent/0.3 (authorized-public-job-feed)");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
