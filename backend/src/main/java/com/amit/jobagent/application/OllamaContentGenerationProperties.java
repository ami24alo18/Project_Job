package com.amit.jobagent.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("job-agent.ollama-content-generation")
record OllamaContentGenerationProperties(
        URI baseUrl,
        Duration timeout,
        int maxRetries,
        int contextWindow,
        String keepAlive) {

    OllamaContentGenerationProperties {
        if (baseUrl == null) baseUrl = URI.create("http://127.0.0.1:11434");
        String scheme = baseUrl.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !(baseUrl.getPath() == null || baseUrl.getPath().isBlank() || "/".equals(baseUrl.getPath()))) {
            throw new IllegalArgumentException("OLLAMA_BASE_URL must be an HTTP(S) origin without credentials or a path");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) timeout = Duration.ofSeconds(180);
        maxRetries = Math.max(0, Math.min(maxRetries, 2));
        if (contextWindow < 4_096) contextWindow = 8_192;
        if (keepAlive == null || !keepAlive.matches("(?:0|[1-9][0-9]*[smh])")) keepAlive = "15m";
    }

    URI chatEndpoint() {
        return baseUrl.resolve("/api/chat");
    }
}
