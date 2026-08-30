package com.amit.jobagent.jobsource.discovery;

import java.util.List;

final class CareerSiteDiscoveryTestSupport {
    private CareerSiteDiscoveryTestSupport() {}

    static CareerSiteDiscoveryProperties properties() {
        return properties(true, 3, 1_024, 2_048, 20, 10_000, 1_000, 2, 10, 300);
    }

    static CareerSiteDiscoveryProperties properties(
            boolean enabled,
            int redirects,
            int compressedBytes,
            int decompressedBytes,
            int compressionRatio,
            int markupCharacters,
            int markupNodes,
            int concurrent,
            int requestsPerMinute,
            int cacheSeconds) {
        return new CareerSiteDiscoveryProperties(enabled, List.of(443), 500, 1_000, redirects,
                compressedBytes, decompressedBytes, compressionRatio, markupCharacters, markupNodes,
                concurrent, requestsPerMinute, cacheSeconds);
    }
}
