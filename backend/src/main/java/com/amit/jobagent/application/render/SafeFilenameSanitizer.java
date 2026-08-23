package com.amit.jobagent.application.render;

import com.amit.jobagent.application.ArtifactType;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/** Produces portable file names without path separators or Windows device names. */
public final class SafeFilenameSanitizer {
    private static final int MAX_COMPONENT_CODE_POINTS = 48;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private SafeFilenameSanitizer() {
    }

    public static String resumeFileName(
            String fullName,
            String role,
            String company,
            LocalDate artifactDate,
            ArtifactType artifactType) {
        if (artifactDate == null) {
            throw new IllegalArgumentException("artifactDate is required");
        }
        var format = ArtifactFormat.forType(artifactType);
        return String.join(
                        "_",
                        sanitizeComponent(fullName),
                        sanitizeComponent(role),
                        sanitizeComponent(company),
                        DATE.format(artifactDate))
                + "." + format.extension;
    }

    public static String sanitizeComponent(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        StringBuilder sanitized = new StringBuilder();
        boolean previousWasSeparator = false;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '-') {
                sanitized.appendCodePoint(codePoint);
                previousWasSeparator = false;
            } else if (!previousWasSeparator && !sanitized.isEmpty()) {
                sanitized.append('_');
                previousWasSeparator = true;
            }
        }
        while (!sanitized.isEmpty() && sanitized.charAt(sanitized.length() - 1) == '_') {
            sanitized.setLength(sanitized.length() - 1);
        }
        if (sanitized.isEmpty()) {
            return "Unknown";
        }
        String result = truncateByCodePoints(sanitized.toString(), MAX_COMPONENT_CODE_POINTS);
        if (WINDOWS_RESERVED.contains(result.toUpperCase(Locale.ROOT))) {
            result = "_" + result;
        }
        return result;
    }

    private static String truncateByCodePoints(String value, int limit) {
        int count = value.codePointCount(0, value.length());
        if (count <= limit) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }
}
