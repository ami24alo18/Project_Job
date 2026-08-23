package com.amit.jobagent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-agent")
public record JobAgentProperties(String allowedFrontendOrigin, N8n n8n, Minio minio, Security security, Documents documents) {
    public record N8n(String webhookSecret) {}
    public record Minio(String url, String accessKey, String secretKey, String bucket) {}
    public record Security(String username, String password) {}
    public record Documents(long maximumSizeBytes) {}
}
