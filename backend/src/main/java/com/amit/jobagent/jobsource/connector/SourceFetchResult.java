package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.job.JobSourceType;
import java.util.List;
import java.util.Objects;

/** Complete or safely bounded partial result from one connector invocation. */
public record SourceFetchResult(
        JobSourceType sourceType,
        List<RawJobRecord> records,
        List<SourceRecordError> recordErrors,
        int pagesFetched,
        boolean complete,
        SourceCheckpoint nextCheckpoint) {

    public SourceFetchResult {
        sourceType = Objects.requireNonNull(sourceType);
        records = List.copyOf(records);
        recordErrors = List.copyOf(recordErrors);
        nextCheckpoint = nextCheckpoint == null ? SourceCheckpoint.beginning() : nextCheckpoint;
        if (pagesFetched < 0) {
            throw new IllegalArgumentException("pagesFetched cannot be negative");
        }
    }

    public int discoveredCount() {
        return records.size() + recordErrors.size();
    }

    public boolean partial() {
        return !complete || !recordErrors.isEmpty();
    }
}
