package com.amit.jobagent.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class ApplicationGenerationMetrics {
    private final MeterRegistry registry;
    private final ContentGenerationProperties config;

    ApplicationGenerationMetrics(MeterRegistry registry, ContentGenerationProperties config) {
        this.registry = registry;
        this.config = config;
    }

    void requested() { counter("job_agent.application.generation.requests", "result", "requested"); }
    void succeeded() { counter("job_agent.application.generation.requests", "result", "succeeded"); }
    void failed() { counter("job_agent.application.generation.requests", "result", "failed"); }
    void cacheHit() { counter("job_agent.application.generation.cache", "result", "hit"); }
    void quotaRejected() { counter("job_agent.application.generation.quota", "result", "rejected"); }
    void renderingFailed() { counter("job_agent.application.document.rendering", "result", "failed"); }
    void validationFailed(String code) { counter("job_agent.application.validation", "code", safeTag(code)); }

    void duration(long nanoseconds, boolean success) {
        registry.timer("job_agent.application.generation.duration", "result", success ? "succeeded" : "failed",
                "prompt_version", config.promptVersion(), "schema_version", config.schemaVersion())
                .record(Duration.ofNanos(Math.max(0, nanoseconds)));
    }

    void provider(long nanoseconds, Long inputTokens, Long outputTokens) {
        registry.timer("job_agent.application.provider.duration", "provider", "openai", "model", config.model())
                .record(Duration.ofNanos(Math.max(0, nanoseconds)));
        if (inputTokens != null) registry.counter("job_agent.application.provider.tokens", "direction", "input", "model", config.model()).increment(inputTokens);
        if (outputTokens != null) registry.counter("job_agent.application.provider.tokens", "direction", "output", "model", config.model()).increment(outputTokens);
    }

    private void counter(String name, String tag, String value) {
        registry.counter(name, tag, value, "prompt_version", config.promptVersion(), "schema_version", config.schemaVersion()).increment();
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
