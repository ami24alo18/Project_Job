package com.amit.jobagent.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("job-agent.ats-scoring")
record AtsScoringProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Duration timeout,
        int maxDocumentCharacters) {
    AtsScoringProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8091" : baseUrl.trim();
        token = token == null ? "" : token.trim();
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(60) : timeout;
        maxDocumentCharacters = maxDocumentCharacters < 1 ? 32_000 : maxDocumentCharacters;
    }
}
