package com.amit.jobagent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-agent.ingestion")
public record JobIngestionProperties(
        int maximumDescriptionCharacters,
        int maximumResponseBytes,
        int connectTimeoutMillis,
        int requestTimeoutMillis,
        int maximumRetries,
        int retryBaseDelayMillis,
        int executorCorePoolSize,
        int executorMaximumPoolSize,
        int executorQueueCapacity,
        int staleRunAfterMinutes) {
}
