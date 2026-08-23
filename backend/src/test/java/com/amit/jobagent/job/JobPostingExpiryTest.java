package com.amit.jobagent.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobPostingExpiryTest {
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000201");
    private static final UUID FIRST_RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000201");
    private static final UUID SECOND_RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000202");
    private static final Instant FIRST_SEEN = Instant.parse("2026-08-20T10:00:00Z");

    @Test
    void sameStructuredExpiryTransitionsToExpiredWhenTimePassesWithoutAContentChange() {
        var expiry = Instant.parse("2026-08-22T10:00:00Z");
        var posting = new JobPosting(
                normalized(expiry, "same-source-hash", JobPostingStatus.READY_FOR_EVALUATION),
                FIRST_RUN_ID,
                null,
                FIRST_SEEN);

        boolean contentChanged = posting.observeProvider(
                normalized(expiry, "same-source-hash", JobPostingStatus.EXPIRED),
                SECOND_RUN_ID,
                Instant.parse("2026-08-22T10:00:01Z"));

        assertThat(contentChanged).isFalse();
        assertThat(posting.status()).isEqualTo(JobPostingStatus.EXPIRED);
        assertThat(posting.lastSeenRunId()).isEqualTo(SECOND_RUN_ID);
    }

    @Test
    void correctedFutureExpiryRestoresAnExpiredProviderJob() {
        var posting = new JobPosting(
                normalized(
                        Instant.parse("2026-08-21T10:00:00Z"),
                        "past-expiry-hash",
                        JobPostingStatus.EXPIRED),
                FIRST_RUN_ID,
                null,
                FIRST_SEEN);

        boolean contentChanged = posting.observeProvider(
                normalized(
                        Instant.parse("2026-09-30T10:00:00Z"),
                        "future-expiry-hash",
                        JobPostingStatus.READY_FOR_EVALUATION),
                SECOND_RUN_ID,
                Instant.parse("2026-08-22T10:00:00Z"));

        assertThat(contentChanged).isTrue();
        assertThat(posting.status()).isEqualTo(JobPostingStatus.READY_FOR_EVALUATION);
    }

    @Test
    void expiryObservationDoesNotOverrideArchivedOrDuplicateLifecycleStates() {
        var ready = normalized(null, "ready-hash", JobPostingStatus.READY_FOR_EVALUATION);
        var expired = normalized(
                Instant.parse("2026-08-21T10:00:00Z"),
                "expired-hash",
                JobPostingStatus.EXPIRED);
        var archived = new JobPosting(ready, FIRST_RUN_ID, null, FIRST_SEEN);
        archived.archive();
        var duplicate = new JobPosting(ready, FIRST_RUN_ID, null, FIRST_SEEN);
        duplicate.markDuplicate(UUID.fromString("10000000-0000-0000-0000-000000000201"));

        archived.observeProvider(expired, SECOND_RUN_ID, Instant.parse("2026-08-22T10:00:00Z"));
        duplicate.observeProvider(expired, SECOND_RUN_ID, Instant.parse("2026-08-22T10:00:00Z"));

        assertThat(archived.status()).isEqualTo(JobPostingStatus.ARCHIVED);
        assertThat(duplicate.status()).isEqualTo(JobPostingStatus.DUPLICATE);
    }

    private static NormalizedJob normalized(
            Instant expiresAt, String contentHash, JobPostingStatus initialStatus) {
        return new NormalizedJob(
                SOURCE_ID,
                JobSourceType.LEVER,
                "provider-job",
                "Example Systems",
                "Backend Engineer",
                "Example City",
                "US",
                WorkplaceType.HYBRID,
                EmploymentType.FULL_TIME,
                "Engineering",
                "Platform",
                "Build safe services",
                false,
                "https://jobs.example.test/provider-job",
                "https://jobs.example.test/provider-job",
                "https://jobs.example.test/provider-job",
                null,
                null,
                null,
                SalaryInterval.UNSPECIFIED,
                Instant.parse("2026-08-20T08:00:00Z"),
                Instant.parse("2026-08-21T08:00:00Z"),
                expiresAt,
                "fingerprint",
                contentHash,
                initialStatus);
    }
}
