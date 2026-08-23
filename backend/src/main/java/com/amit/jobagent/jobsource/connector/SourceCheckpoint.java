package com.amit.jobagent.jobsource.connector;

/** Opaque, provider-owned position used only when resuming a bounded fetch. */
public record SourceCheckpoint(String value) {
    public SourceCheckpoint {
        value = value == null || value.isBlank() ? null : value.trim();
    }

    public static SourceCheckpoint beginning() {
        return new SourceCheckpoint(null);
    }
}
