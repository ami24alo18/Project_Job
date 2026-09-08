package com.amit.jobagent.application;

import java.util.Locale;

final class ApplicationGenerationFailure {
    private ApplicationGenerationFailure() {}

    static String code(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String candidate = message.trim().toUpperCase(Locale.ROOT);
                if (candidate.matches("(?:OPENROUTER|OLLAMA|CONTENT_GENERATION)_[A-Z0-9_]{1,61}")) {
                    return candidate;
                }
            }
        }
        String fallback = failure.getClass().getSimpleName()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toUpperCase(Locale.ROOT);
        return fallback.isBlank() ? "GENERATION_FAILED" : limit(fallback, 80);
    }

    static String message(Throwable failure) {
        return switch (code(failure)) {
            case "OPENROUTER_API_KEY_MISSING" -> "OpenRouter API key is not configured";
            case "OPENROUTER_HTTP_401", "OPENROUTER_HTTP_403" -> "OpenRouter rejected the configured API key or account permissions";
            case "OPENROUTER_HTTP_402" -> "OpenRouter requires credits or a permitted free-model account configuration";
            case "OPENROUTER_HTTP_404" -> "The configured OpenRouter model is unavailable or has no endpoint matching the request and privacy settings";
            case "OPENROUTER_HTTP_429" -> "OpenRouter free-model rate limit was reached; retry later";
            case "OPENROUTER_UNAVAILABLE" -> "OpenRouter could not be reached";
            case "OPENROUTER_REQUEST_INTERRUPTED" -> "The OpenRouter request was interrupted";
            case "CONTENT_GENERATION_EMPTY_RESPONSE" -> "The model returned an empty response";
            case "CONTENT_GENERATION_OUTPUT_TRUNCATED" -> "The model response exceeded the configured output limit";
            case "CONTENT_GENERATION_INVALID_RESPONSE" -> "The model returned content that did not match the required structure";
            default -> "Application content generation failed safely (" + code(failure) + ")";
        };
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
