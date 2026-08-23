package com.amit.jobagent.application.render;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Structured, already-validated content consumed by all resume artifact renderers. */
public record ResumeDocumentModel(
        ResumeContact contact,
        String targetRole,
        String targetCompany,
        String headline,
        List<String> summaryParagraphs,
        List<String> skills,
        List<ResumeEntry> experience,
        List<ResumeEntry> projects,
        List<ResumeEntry> education,
        LocalDate artifactDate) {

    public ResumeDocumentModel {
        contact = Objects.requireNonNull(contact, "contact is required");
        targetRole = requireText(targetRole, "targetRole");
        targetCompany = requireText(targetCompany, "targetCompany");
        headline = optionalText(headline);
        summaryParagraphs = nonBlankText(summaryParagraphs, "summaryParagraphs");
        skills = nonBlankText(skills, "skills");
        experience = immutableEntries(experience, "experience");
        projects = immutableEntries(projects, "projects");
        education = immutableEntries(education, "education");
        artifactDate = Objects.requireNonNull(artifactDate, "artifactDate is required");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> nonBlankText(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> Objects.requireNonNull(value, field + " must not contain null"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static List<ResumeEntry> immutableEntries(List<ResumeEntry> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        values.forEach(value -> Objects.requireNonNull(value, field + " must not contain null"));
        return List.copyOf(values);
    }
}
