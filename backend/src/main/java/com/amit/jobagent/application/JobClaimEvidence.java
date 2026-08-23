package com.amit.jobagent.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact ingested job evidence that may support JOB_REFERENCE claim atoms. */
public record JobClaimEvidence(Map<UUID, String> requirements, Map<String, String> fields) {
    public JobClaimEvidence {
        requirements = immutableNonNull(requirements);
        fields = immutableNonNull(fields);
    }

    public static JobClaimEvidence empty() {
        return new JobClaimEvidence(Map.of(), Map.of());
    }

    private static <K> Map<K, String> immutableNonNull(Map<K, String> values) {
        var copy = new LinkedHashMap<K, String>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    copy.put(key, value);
                }
            });
        }
        return Map.copyOf(copy);
    }
}
