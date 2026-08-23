package com.amit.jobagent.application.render;

import java.util.List;
import java.util.Objects;

/** A single experience, project, or education entry in the canonical resume model. */
public record ResumeEntry(
        String title,
        String organization,
        String location,
        String dateRange,
        List<String> bullets) {

    public ResumeEntry {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        title = title.trim();
        organization = optionalText(organization);
        location = optionalText(location);
        dateRange = optionalText(dateRange);
        bullets = nonBlankValues(bullets);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> nonBlankValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> Objects.requireNonNull(value, "bullets must not contain null"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
