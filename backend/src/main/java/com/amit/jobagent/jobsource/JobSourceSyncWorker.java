package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobPostingService;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.JobSourceConnector;
import com.amit.jobagent.jobsource.connector.SourceCheckpoint;
import com.amit.jobagent.jobsource.connector.SourceFetchErrorCode;
import com.amit.jobagent.jobsource.connector.SourceFetchException;
import com.amit.jobagent.jobsource.connector.SourceFetchRequest;
import com.amit.jobagent.jobsource.connector.SourceFetchResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class JobSourceSyncWorker {
    private static final Logger LOG = LoggerFactory.getLogger(JobSourceSyncWorker.class);
    private static final String LEVER_SCAN_CHECKPOINT_PREFIX = "lever-scan-v1";

    private final JobSourceRunPersistenceService persistence;
    private final JobPostingService jobs;
    private final Map<JobSourceType, JobSourceConnector> connectors;
    private final Map<JobSourceType, JobSourceNormalizer> normalizers;

    JobSourceSyncWorker(
            JobSourceRunPersistenceService persistence,
            JobPostingService jobs,
            List<JobSourceConnector> connectors,
            List<JobSourceNormalizer> normalizers) {
        this.persistence = persistence;
        this.jobs = jobs;
        this.connectors = new EnumMap<>(JobSourceType.class);
        connectors.forEach(connector -> this.connectors.put(connector.supportedType(), connector));
        this.normalizers = new EnumMap<>(JobSourceType.class);
        normalizers.forEach(normalizer -> this.normalizers.put(normalizer.supportedType(), normalizer));
    }

    void execute(UUID runId) {
        LOG.info("Starting authorized job-source synchronization runId={}", runId);
        try {
            var source = persistence.begin(runId);
            var connector = require(connectors, source.sourceType(), "connector");
            var normalizer = require(normalizers, source.sourceType(), "normalizer");
            String priorCheckpoint = source.sourceType() == JobSourceType.LEVER
                    ? persistence.resumableCheckpoint(source.sourceId(), runId)
                    : null;
            var scan = ScanCycle.start(source, runId, priorCheckpoint);
            var fetched = connector.fetch(new SourceFetchRequest(
                    source.sourceType(),
                    source.providerIdentifier(),
                    source.region(),
                    source.pageSize(),
                    source.maximumPages(),
                    scan.fetchCheckpoint()));

            int created = 0;
            int updated = 0;
            int unchanged = 0;
            int duplicates = 0;
            int failed = fetched.recordErrors().size();
            var problems = new ArrayList<RunProblem>();
            for (var error : fetched.recordErrors()) {
                problems.add(new RunProblem(error.externalId(), error.code(), error.message()));
            }
            for (var raw : fetched.records()) {
                try {
                    var outcome = jobs.ingest(normalizer.normalize(raw, source), scan.seenRunId());
                    switch (outcome.action()) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case UNCHANGED -> unchanged++;
                        case DUPLICATE -> duplicates++;
                    }
                } catch (Exception e) {
                    failed++;
                    problems.add(new RunProblem(
                            raw.externalId(),
                            "NORMALIZATION_OR_PERSISTENCE_FAILED",
                            "A job record could not be normalized or stored"));
                }
            }

            int removed = 0;
            boolean fullySuccessful = fetched.complete() && failed == 0 && !scan.tainted();
            if (fullySuccessful) {
                removed = jobs.markMissingAfterSuccessfulRun(
                        source.sourceId(), scan.seenRunId(), source.missingRunThreshold()).removedCount();
            } else if (fetched.complete() && failed == 0 && scan.tainted()) {
                LOG.warn(
                        "Skipping missing-job accounting for tainted resumed scan runId={} sourceId={} scanRunId={}",
                        runId,
                        source.sourceId(),
                        scan.seenRunId());
            }

            var status = fullySuccessful
                    ? JobSourceRunStatus.SUCCEEDED
                    : JobSourceRunStatus.PARTIAL_SUCCESS;
            var metrics = new RunMetrics(
                    fetched.discoveredCount(), created, updated, unchanged, duplicates, failed, removed);
            persistence.complete(
                    runId,
                    status,
                    scan.checkpointAfter(source, fetched, failed > 0),
                    metrics,
                    problems);
            LOG.info(
                    "Completed authorized job-source synchronization runId={} sourceId={} sourceType={} status={} discovered={} created={} updated={} unchanged={} duplicates={} failed={} removed={}",
                    runId,
                    source.sourceId(),
                    source.sourceType(),
                    status,
                    metrics.discovered(),
                    metrics.created(),
                    metrics.updated(),
                    metrics.unchanged(),
                    metrics.duplicates(),
                    metrics.failed(),
                    metrics.removed());
        } catch (SourceFetchException e) {
            LOG.warn("Authorized job-source synchronization failed runId={} safeErrorCode={}", runId, e.code());
            persistence.fail(runId, e.code().name(), safeMessage(e));
        } catch (Exception e) {
            LOG.error(
                    "Authorized job-source synchronization failed runId={} safeErrorCode=UNEXPECTED_SYNC_FAILURE",
                    runId);
            persistence.fail(runId, "UNEXPECTED_SYNC_FAILURE", "The synchronization run failed safely");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileStaleRuns() {
        persistence.reconcileStale();
    }

    private static <T> T require(Map<JobSourceType, T> map, JobSourceType type, String kind) {
        var value = map.get(type);
        if (value == null) {
            throw new SourceFetchException(
                    SourceFetchErrorCode.UNSUPPORTED_SOURCE,
                    "No " + kind + " is configured for this source type");
        }
        return value;
    }

    private static String safeMessage(SourceFetchException e) {
        return switch (e.code()) {
            case RATE_LIMITED -> "The provider rate-limited the request";
            case TIMEOUT -> "The provider request timed out";
            case PERMANENT_HTTP_ERROR -> "The provider rejected the public-feed request";
            case RESPONSE_TOO_LARGE -> "The provider response exceeded the configured limit";
            case INVALID_PAYLOAD -> "The provider returned an invalid payload";
            default -> "The authorized provider feed could not be fetched";
        };
    }

    private record ScanCycle(UUID seenRunId, SourceCheckpoint fetchCheckpoint, boolean tainted, boolean lever) {
        private static ScanCycle start(JobSourceRunContext source, UUID currentRunId, String persisted) {
            if (source.sourceType() != JobSourceType.LEVER || persisted == null) {
                return fresh(currentRunId, source.sourceType() == JobSourceType.LEVER);
            }
            try {
                String[] fields = persisted.split("\\|", -1);
                if (fields.length != 6
                        || !LEVER_SCAN_CHECKPOINT_PREFIX.equals(fields[0])
                        || !source.region().name().equals(fields[4])
                        || !source.providerIdentifier().equals(fields[5])) {
                    return fresh(currentRunId, true);
                }
                UUID anchorRunId = UUID.fromString(fields[1]);
                int offset = Integer.parseInt(fields[2]);
                if (offset < 0 || !("0".equals(fields[3]) || "1".equals(fields[3]))) {
                    return fresh(currentRunId, true);
                }
                return new ScanCycle(
                        anchorRunId,
                        new SourceCheckpoint(Integer.toString(offset)),
                        "1".equals(fields[3]),
                        true);
            } catch (RuntimeException invalidCheckpoint) {
                return fresh(currentRunId, true);
            }
        }

        private static ScanCycle fresh(UUID currentRunId, boolean lever) {
            return new ScanCycle(currentRunId, SourceCheckpoint.beginning(), false, lever);
        }

        private String checkpointAfter(
                JobSourceRunContext source, SourceFetchResult fetched, boolean currentRunTainted) {
            String next = fetched.nextCheckpoint().value();
            if (!lever || fetched.complete() || next == null) {
                return next;
            }
            try {
                int offset = Integer.parseInt(next);
                if (offset < 0) {
                    return next;
                }
                return String.join(
                        "|",
                        LEVER_SCAN_CHECKPOINT_PREFIX,
                        seenRunId.toString(),
                        Integer.toString(offset),
                        tainted || currentRunTainted ? "1" : "0",
                        source.region().name(),
                        source.providerIdentifier());
            } catch (NumberFormatException invalidCheckpoint) {
                return next;
            }
        }
    }
}
