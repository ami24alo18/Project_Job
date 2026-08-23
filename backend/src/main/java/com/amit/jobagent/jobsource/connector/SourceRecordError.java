package com.amit.jobagent.jobsource.connector;

/** Safe per-record failure detail; it intentionally excludes provider payloads. */
public record SourceRecordError(int position, String externalId, String code, String message) {
}
