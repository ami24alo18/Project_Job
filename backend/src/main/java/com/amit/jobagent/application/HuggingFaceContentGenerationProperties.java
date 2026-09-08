package com.amit.jobagent.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("job-agent.huggingface-content-generation")
record HuggingFaceContentGenerationProperties(
        URI baseUrl,
        Duration timeout,
        int maxRetries) {

    HuggingFaceContentGenerationProperties {
        if (baseUrl == null) baseUrl = URI.create("https://router.huggingface.co/v1");
        if (!"https".equalsIgnoreCase(baseUrl.getScheme())
                || !"router.huggingface.co".equalsIgnoreCase(baseUrl.getHost())
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !"/v1".equals(baseUrl.getPath())) {
            throw new IllegalArgumentException(
                    "HUGGINGFACE_BASE_URL must be https://router.huggingface.co/v1");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(180);
        maxRetries = Math.max(0, Math.min(maxRetries, 2));
    }

    URI chatEndpoint() {
        return URI.create(baseUrl.toString() + "/chat/completions");
    }
}
