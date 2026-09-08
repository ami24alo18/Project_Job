package com.amit.jobagent.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("job-agent.openrouter-content-generation")
record OpenRouterContentGenerationProperties(
        URI baseUrl,
        Duration timeout,
        int maxRetries,
        String httpReferer,
        String appTitle,
        boolean allowDataCollection) {

    OpenRouterContentGenerationProperties {
        if (baseUrl == null) baseUrl = URI.create("https://openrouter.ai/api/v1");
        if (!"https".equalsIgnoreCase(baseUrl.getScheme())
                || !"openrouter.ai".equalsIgnoreCase(baseUrl.getHost())
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !"/api/v1".equals(baseUrl.getPath())) {
            throw new IllegalArgumentException("OPENROUTER_BASE_URL must be https://openrouter.ai/api/v1");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(120);
        maxRetries = Math.max(0, Math.min(maxRetries, 2));
        if (httpReferer == null || httpReferer.isBlank()) httpReferer = "http://localhost:3000";
        if (appTitle == null || appTitle.isBlank()) appTitle = "Job Application Agent";
    }

    URI chatEndpoint() {
        return URI.create(baseUrl.toString() + "/chat/completions");
    }
}
