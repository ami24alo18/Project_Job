package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.job.JobSourceType;
import java.util.Objects;

/** Provider-neutral, bounded input to a public-feed connector. */
public record SourceFetchRequest(
        JobSourceType sourceType,
        String providerIdentifier,
        SourceRegion region,
        int pageSize,
        int maximumPages,
        SourceCheckpoint checkpoint) {

    public SourceFetchRequest {
        sourceType = Objects.requireNonNull(sourceType, "sourceType is required");
        providerIdentifier = Objects.requireNonNull(providerIdentifier, "providerIdentifier is required").trim();
        region = region == null ? SourceRegion.DEFAULT : region;
        checkpoint = checkpoint == null ? SourceCheckpoint.beginning() : checkpoint;
        if (providerIdentifier.isEmpty()) {
            throw new IllegalArgumentException("providerIdentifier is required");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (maximumPages < 1 || maximumPages > 100) {
            throw new IllegalArgumentException("maximumPages must be between 1 and 100");
        }
    }
}
