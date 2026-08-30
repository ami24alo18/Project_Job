package com.amit.jobagent.jobsource.discovery;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-agent.career-discovery")
public record CareerSiteDiscoveryProperties(
        boolean enabled,
        List<Integer> allowedPorts,
        int connectTimeoutMillis,
        int requestTimeoutMillis,
        int maximumRedirects,
        int maximumCompressedBytes,
        int maximumDecompressedBytes,
        int maximumCompressionRatio,
        int maximumMarkupCharacters,
        int maximumMarkupNodes,
        int maximumConcurrentRequests,
        int maximumRequestsPerHostPerMinute,
        int cacheTtlSeconds) {

    public CareerSiteDiscoveryProperties {
        allowedPorts = allowedPorts == null ? List.of(443) : List.copyOf(allowedPorts);
        if (allowedPorts.isEmpty() || allowedPorts.stream().anyMatch(port -> port == null || port < 1 || port > 65535)) {
            throw new IllegalArgumentException("career discovery allowed ports are invalid");
        }
        positive(connectTimeoutMillis, "connectTimeoutMillis");
        positive(requestTimeoutMillis, "requestTimeoutMillis");
        positive(maximumCompressedBytes, "maximumCompressedBytes");
        positive(maximumDecompressedBytes, "maximumDecompressedBytes");
        positive(maximumCompressionRatio, "maximumCompressionRatio");
        positive(maximumMarkupCharacters, "maximumMarkupCharacters");
        positive(maximumMarkupNodes, "maximumMarkupNodes");
        positive(maximumConcurrentRequests, "maximumConcurrentRequests");
        positive(maximumRequestsPerHostPerMinute, "maximumRequestsPerHostPerMinute");
        positive(cacheTtlSeconds, "cacheTtlSeconds");
        if (maximumRedirects < 0 || maximumRedirects > 10) {
            throw new IllegalArgumentException("maximumRedirects must be between 0 and 10");
        }
    }

    private static void positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
    }
}
