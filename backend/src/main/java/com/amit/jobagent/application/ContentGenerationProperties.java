package com.amit.jobagent.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("job-agent.content-generation")
public record ContentGenerationProperties(
        boolean enabled,
        String provider,
        boolean automationEnabled,
        String model,
        String reasoningEffort,
        Duration timeout,
        int maxRetries,
        int dailyLimit,
        long maxInputTokens,
        long maxOutputTokens,
        int maxInputCharacters,
        String promptVersion,
        String schemaVersion,
        String templateVersion) {

    public ContentGenerationProperties {
        provider = provider == null || provider.isBlank()
                ? "openai" : provider.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("deterministic", "openai", "ollama", "openrouter", "huggingface").contains(provider)) {
            throw new IllegalArgumentException("Unsupported content-generation provider: " + provider);
        }
        if (model == null || model.isBlank()) model = "gpt-5.6-terra";
        if (reasoningEffort == null || reasoningEffort.isBlank()) reasoningEffort = "low";
        if (timeout == null) timeout = Duration.ofSeconds(30);
        maxRetries = Math.max(0, maxRetries);
        if (dailyLimit <= 0) dailyLimit = 20;
        if (maxInputTokens <= 0) maxInputTokens = 24_000;
        if (maxOutputTokens <= 0) maxOutputTokens = 8_000;
        if (maxInputCharacters <= 0) maxInputCharacters = 60_000;
        if (promptVersion == null || promptVersion.isBlank()) promptVersion = "v1";
        if (schemaVersion == null || schemaVersion.isBlank()) schemaVersion = "v1";
        if (templateVersion == null || templateVersion.isBlank()) templateVersion = "master-resume-classic-v2";
    }
}
