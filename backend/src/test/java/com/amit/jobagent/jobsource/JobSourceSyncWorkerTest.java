package com.amit.jobagent.jobsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amit.jobagent.job.EmploymentType;
import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobIngestionAction;
import com.amit.jobagent.job.JobIngestionOutcome;
import com.amit.jobagent.job.JobPostingService;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.job.MissingJobResult;
import com.amit.jobagent.job.SalaryInterval;
import com.amit.jobagent.job.WorkplaceType;
import com.amit.jobagent.jobsource.connector.JobSourceConnector;
import com.amit.jobagent.jobsource.connector.RawJobRecord;
import com.amit.jobagent.jobsource.connector.SourceCheckpoint;
import com.amit.jobagent.jobsource.connector.SourceFetchErrorCode;
import com.amit.jobagent.jobsource.connector.SourceFetchException;
import com.amit.jobagent.jobsource.connector.SourceFetchRequest;
import com.amit.jobagent.jobsource.connector.SourceFetchResult;
import com.amit.jobagent.jobsource.connector.SourceRecordError;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobSourceSyncWorkerTest {
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000102");
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
    private static final JobSourceRunContext SOURCE = source(RUN_ID);
    private static final JobSourceRunContext SECOND_SOURCE = source(SECOND_RUN_ID);

    private JobSourceRunPersistenceService persistence;
    private JobPostingService jobs;
    private JobSourceConnector connector;
    private JobSourceNormalizer normalizer;
    private JobSourceSyncWorker worker;

    @BeforeEach
    void setUp() {
        persistence = mock(JobSourceRunPersistenceService.class);
        jobs = mock(JobPostingService.class);
        connector = mock(JobSourceConnector.class);
        normalizer = mock(JobSourceNormalizer.class);
        when(connector.supportedType()).thenReturn(JobSourceType.LEVER);
        when(normalizer.supportedType()).thenReturn(JobSourceType.LEVER);
        worker = new JobSourceSyncWorker(persistence, jobs, List.of(connector), List.of(normalizer));
        when(persistence.begin(RUN_ID)).thenReturn(SOURCE);
    }

    @Test
    void successfulFetchPersistsAllMetricsAndMarksMissingJobs() {
        var records = List.of(raw("created"), raw("updated"), raw("unchanged"), raw("duplicate"));
        when(connector.fetch(any(SourceFetchRequest.class))).thenReturn(new SourceFetchResult(
                JobSourceType.LEVER, records, List.of(), 1, true, new SourceCheckpoint("offset=4")));
        for (var raw : records) {
            when(normalizer.normalize(raw, SOURCE)).thenReturn(candidate(raw.externalId()));
        }
        when(jobs.ingest(any(JobCandidate.class), eq(RUN_ID))).thenReturn(
                outcome(JobIngestionAction.CREATED),
                outcome(JobIngestionAction.UPDATED),
                outcome(JobIngestionAction.UNCHANGED),
                outcome(JobIngestionAction.DUPLICATE));
        when(jobs.markMissingAfterSuccessfulRun(SOURCE_ID, RUN_ID, 2)).thenReturn(new MissingJobResult(3));

        worker.execute(RUN_ID);

        var request = ArgumentCaptor.forClass(SourceFetchRequest.class);
        verify(connector).fetch(request.capture());
        assertThat(request.getValue()).isEqualTo(new SourceFetchRequest(
                JobSourceType.LEVER,
                "example-site",
                SourceRegion.GLOBAL,
                25,
                4,
                SourceCheckpoint.beginning()));
        verify(jobs).markMissingAfterSuccessfulRun(SOURCE_ID, RUN_ID, 2);
        verify(persistence).complete(
                RUN_ID,
                JobSourceRunStatus.SUCCEEDED,
                "offset=4",
                new RunMetrics(4, 1, 1, 1, 1, 0, 3),
                List.of());
        verify(persistence, never()).fail(any(), any(), any());
    }

    @Test
    void connectorRecordErrorProducesPartialRunAndSkipsMissingDetection() {
        var valid = raw("valid-record");
        var error = new SourceRecordError(
                1, "bad-record", "MALFORMED_RECORD", "The provider record was malformed");
        when(connector.fetch(any(SourceFetchRequest.class))).thenReturn(new SourceFetchResult(
                JobSourceType.LEVER,
                List.of(valid),
                List.of(error),
                1,
                true,
                new SourceCheckpoint("offset=2")));
        when(normalizer.normalize(valid, SOURCE)).thenReturn(candidate(valid.externalId()));
        when(jobs.ingest(any(JobCandidate.class), eq(RUN_ID))).thenReturn(outcome(JobIngestionAction.CREATED));

        worker.execute(RUN_ID);

        verify(persistence).complete(
                RUN_ID,
                JobSourceRunStatus.PARTIAL_SUCCESS,
                "offset=2",
                new RunMetrics(2, 1, 0, 0, 0, 1, 0),
                List.of(new RunProblem(
                        "bad-record", "MALFORMED_RECORD", "The provider record was malformed")));
        verify(jobs, never()).markMissingAfterSuccessfulRun(any(), any(), any(Integer.class));
        verify(persistence, never()).fail(any(), any(), any());
    }

    @Test
    void leverPageCapResumesAtPersistedOffsetAndUsesOneScanAnchorForSafeMissingDetection() {
        var pageOne = raw("page-one");
        var pageTwo = raw("page-two");
        var pageOneCandidate = candidate(pageOne.externalId());
        var pageTwoCandidate = candidate(pageTwo.externalId());
        when(persistence.begin(SECOND_RUN_ID)).thenReturn(SECOND_SOURCE);
        when(connector.fetch(any(SourceFetchRequest.class))).thenReturn(
                new SourceFetchResult(
                        JobSourceType.LEVER,
                        List.of(pageOne),
                        List.of(),
                        4,
                        false,
                        new SourceCheckpoint("25")),
                new SourceFetchResult(
                        JobSourceType.LEVER,
                        List.of(pageTwo),
                        List.of(),
                        1,
                        true,
                        new SourceCheckpoint("26")));
        when(normalizer.normalize(pageOne, SOURCE)).thenReturn(pageOneCandidate);
        when(normalizer.normalize(pageTwo, SECOND_SOURCE)).thenReturn(pageTwoCandidate);
        when(jobs.ingest(pageOneCandidate, RUN_ID)).thenReturn(outcome(JobIngestionAction.CREATED));
        when(jobs.ingest(pageTwoCandidate, RUN_ID)).thenReturn(outcome(JobIngestionAction.CREATED));
        when(jobs.markMissingAfterSuccessfulRun(SOURCE_ID, RUN_ID, 2)).thenReturn(new MissingJobResult(2));

        worker.execute(RUN_ID);

        var firstCheckpoint = ArgumentCaptor.forClass(String.class);
        verify(persistence).complete(
                eq(RUN_ID),
                eq(JobSourceRunStatus.PARTIAL_SUCCESS),
                firstCheckpoint.capture(),
                eq(new RunMetrics(1, 1, 0, 0, 0, 0, 0)),
                eq(List.of()));
        assertThat(firstCheckpoint.getValue()).isEqualTo(
                "lever-scan-v1|" + RUN_ID + "|25|0|GLOBAL|example-site");
        verify(jobs, never()).markMissingAfterSuccessfulRun(any(), any(), any(Integer.class));

        when(persistence.resumableCheckpoint(SOURCE_ID, SECOND_RUN_ID))
                .thenReturn(firstCheckpoint.getValue());
        worker.execute(SECOND_RUN_ID);

        var requests = ArgumentCaptor.forClass(SourceFetchRequest.class);
        verify(connector, times(2)).fetch(requests.capture());
        assertThat(requests.getAllValues().get(0).checkpoint()).isEqualTo(SourceCheckpoint.beginning());
        assertThat(requests.getAllValues().get(1).checkpoint()).isEqualTo(new SourceCheckpoint("25"));
        verify(jobs).ingest(pageTwoCandidate, RUN_ID);
        verify(jobs).markMissingAfterSuccessfulRun(SOURCE_ID, RUN_ID, 2);
        verify(persistence).complete(
                SECOND_RUN_ID,
                JobSourceRunStatus.SUCCEEDED,
                "26",
                new RunMetrics(1, 1, 0, 0, 0, 0, 2),
                List.of());
    }

    @Test
    void taintedResumedScanCompletesWithoutMissingDetectionAndThenResetsCheckpoint() {
        when(persistence.resumableCheckpoint(SOURCE_ID, RUN_ID)).thenReturn(
                "lever-scan-v1|30000000-0000-0000-0000-000000000099|25|1|GLOBAL|example-site");
        when(connector.fetch(any(SourceFetchRequest.class))).thenReturn(new SourceFetchResult(
                JobSourceType.LEVER,
                List.of(),
                List.of(),
                1,
                true,
                new SourceCheckpoint("25")));

        worker.execute(RUN_ID);

        var request = ArgumentCaptor.forClass(SourceFetchRequest.class);
        verify(connector).fetch(request.capture());
        assertThat(request.getValue().checkpoint()).isEqualTo(new SourceCheckpoint("25"));
        verify(jobs, never()).markMissingAfterSuccessfulRun(any(), any(), any(Integer.class));
        verify(persistence).complete(
                RUN_ID,
                JobSourceRunStatus.PARTIAL_SUCCESS,
                "25",
                new RunMetrics(0, 0, 0, 0, 0, 0, 0),
                List.of());
    }

    @Test
    void connectorFailurePersistsTypedSafeFailure() {
        when(connector.fetch(any(SourceFetchRequest.class))).thenThrow(new SourceFetchException(
                SourceFetchErrorCode.RATE_LIMITED,
                "Unsafe provider details must not be persisted",
                429,
                true,
                Duration.ofSeconds(30),
                null));

        worker.execute(RUN_ID);

        verify(persistence).fail(
                RUN_ID, "RATE_LIMITED", "The provider rate-limited the request");
        verify(normalizer, never()).normalize(any(), any());
        verify(jobs, never()).ingest(any(), any());
        verify(jobs, never()).markMissingAfterSuccessfulRun(any(), any(), any(Integer.class));
        verify(persistence, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void normalizationFailureIsIsolatedAndLaterRecordsStillIngest() {
        var invalid = raw("invalid-record");
        var valid = raw("valid-record");
        when(connector.fetch(any(SourceFetchRequest.class))).thenReturn(new SourceFetchResult(
                JobSourceType.LEVER,
                List.of(invalid, valid),
                List.of(),
                1,
                true,
                new SourceCheckpoint("offset=2")));
        when(normalizer.normalize(invalid, SOURCE)).thenThrow(new IllegalArgumentException("unsafe raw detail"));
        var validCandidate = candidate(valid.externalId());
        when(normalizer.normalize(valid, SOURCE)).thenReturn(validCandidate);
        when(jobs.ingest(validCandidate, RUN_ID)).thenReturn(outcome(JobIngestionAction.UPDATED));

        worker.execute(RUN_ID);

        verify(jobs).ingest(validCandidate, RUN_ID);
        verify(persistence).complete(
                RUN_ID,
                JobSourceRunStatus.PARTIAL_SUCCESS,
                "offset=2",
                new RunMetrics(2, 0, 1, 0, 0, 1, 0),
                List.of(new RunProblem(
                        "invalid-record",
                        "NORMALIZATION_OR_PERSISTENCE_FAILED",
                        "A job record could not be normalized or stored")));
        verify(jobs, never()).markMissingAfterSuccessfulRun(any(), any(), any(Integer.class));
        verify(persistence, never()).fail(any(), any(), any());
    }

    @Test
    void completedAndStaleFailedRunsRejectLateRestartOrCompletion() {
        var now = Instant.parse("2026-08-22T10:00:00Z");
        var metrics = new RunMetrics(1, 1, 0, 0, 0, 0, 0);
        var completed = new JobSourceRun(SOURCE_ID, JobSourceTriggerType.MANUAL, now);

        assertThat(completed.start(now.plusSeconds(1))).isTrue();
        assertThat(completed.complete(
                        JobSourceRunStatus.SUCCEEDED,
                        "done",
                        metrics,
                        now.plusSeconds(2),
                        null,
                        null))
                .isTrue();
        assertThat(completed.start(now.plusSeconds(3))).isFalse();
        assertThat(completed.complete(
                        JobSourceRunStatus.PARTIAL_SUCCESS,
                        "late",
                        metrics,
                        now.plusSeconds(3),
                        "LATE",
                        "Late completion"))
                .isFalse();
        assertThat(completed.status()).isEqualTo(JobSourceRunStatus.SUCCEEDED);
        assertThat(completed.checkpoint()).isEqualTo("done");

        var staleFailed = new JobSourceRun(SOURCE_ID, JobSourceTriggerType.RETRY, now);
        staleFailed.fail("STALE_RUN_RECOVERED", "Recovered stale run", now.plusSeconds(60));

        assertThat(staleFailed.start(now.plusSeconds(61))).isFalse();
        assertThat(staleFailed.complete(
                        JobSourceRunStatus.SUCCEEDED,
                        "late",
                        metrics,
                        now.plusSeconds(61),
                        null,
                        null))
                .isFalse();
        assertThat(staleFailed.status()).isEqualTo(JobSourceRunStatus.FAILED);
        assertThat(staleFailed.safeErrorCode()).isEqualTo("STALE_RUN_RECOVERED");
    }

    @Test
    void applicationReadyReconcilesStaleRuns() {
        worker.reconcileStaleRuns();

        verify(persistence).reconcileStale();
    }

    private static JobIngestionOutcome outcome(JobIngestionAction action) {
        return new JobIngestionOutcome(UUID.randomUUID(), action);
    }

    private static JobSourceRunContext source(UUID runId) {
        return new JobSourceRunContext(
                runId,
                SOURCE_ID,
                "Example Lever",
                JobSourceType.LEVER,
                "example-site",
                SourceRegion.GLOBAL,
                25,
                4,
                2);
    }

    private static RawJobRecord raw(String externalId) {
        return new RawJobRecord(
                externalId,
                "Example Company",
                "Backend Engineer",
                "Example City",
                "US",
                "HYBRID",
                "FULL_TIME",
                "Engineering",
                "Platform",
                "Build fictional services",
                null,
                "https://jobs.example.test/" + externalId,
                "https://jobs.example.test/" + externalId,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static JobCandidate candidate(String externalId) {
        return new JobCandidate(
                SOURCE_ID,
                JobSourceType.LEVER,
                externalId,
                "Example Company",
                "Backend Engineer",
                "Example City",
                "US",
                WorkplaceType.HYBRID,
                EmploymentType.FULL_TIME,
                "Engineering",
                "Platform",
                "Build fictional services",
                false,
                "https://jobs.example.test/" + externalId,
                "https://jobs.example.test/" + externalId,
                null,
                null,
                null,
                SalaryInterval.UNSPECIFIED,
                null,
                null,
                null);
    }
}
