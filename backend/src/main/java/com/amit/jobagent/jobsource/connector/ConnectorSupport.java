package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class ConnectorSupport {
    private ConnectorSupport() {
    }

    static ConnectorHttpSettings settings(JobIngestionProperties properties) {
        ConnectorHttpSettings defaults = ConnectorHttpSettings.defaults();
        int connectMillis = positiveOr(properties.connectTimeoutMillis(),
                Math.toIntExact(defaults.connectTimeout().toMillis()));
        int requestMillis = positiveOr(properties.requestTimeoutMillis(),
                Math.toIntExact(defaults.responseTimeout().toMillis()));
        int maximumBytes = positiveOr(properties.maximumResponseBytes(), defaults.maximumResponseBytes());
        int retries = properties.maximumRetries() > 0 ? Math.min(properties.maximumRetries(), 4) : 2;
        int baseDelay = positiveOr(properties.retryBaseDelayMillis(),
                Math.toIntExact(defaults.initialBackoff().toMillis()));
        long maximumDelay = Math.min(30_000L, Math.max(baseDelay, (long) baseDelay * 8));
        return new ConnectorHttpSettings(
                Duration.ofMillis(connectMillis),
                Duration.ofMillis(requestMillis),
                maximumBytes,
                retries + 1,
                Duration.ofMillis(baseDelay),
                Duration.ofMillis(maximumDelay),
                defaults.userAgent());
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Instant instant(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            if (value.isIntegralNumber()) {
                long epoch = value.longValue();
                return Math.abs(epoch) >= 100_000_000_000L
                        ? Instant.ofEpochMilli(epoch)
                        : Instant.ofEpochSecond(epoch);
            }
            String raw = value.asText();
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignored) {
                try {
                    return OffsetDateTime.parse(raw).toInstant();
                } catch (RuntimeException offsetFailure) {
                    return LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC);
                }
            }
        } catch (RuntimeException invalidTimestamp) {
            return null;
        }
    }

    static String firstName(JsonNode array) {
        if (array == null || !array.isArray()) {
            return null;
        }
        for (JsonNode item : array) {
            String value = text(item, "name");
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static SourceRecordError malformed(int position, JsonNode node) {
        return new SourceRecordError(position, truncate(text(node, "id"), 200),
                "MALFORMED_RECORD", "The provider record is missing a stable ID or title");
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static String truncate(String value, int maximum) {
        return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
