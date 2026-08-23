package com.amit.jobagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class JobPostingServiceIntegrationTest {
    private static final UUID SOURCE_ONE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID RUN_ONE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RUN_TWO = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID RUN_THREE = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID RUN_FOUR = UUID.fromString("30000000-0000-0000-0000-000000000004");

    @Autowired JobPostingService service;
    @Autowired JobPostingRepository repository;

    @Test
    void providerIdentityUpdatesExistingRecordAndReportsUnchangedContent() {
        JobCandidate original = provider(
                SOURCE_ONE, "provider-job-1", "Example Systems", "Backend Engineer", "Example City",
                "Provider description one", "https://jobs.example.test/openings/1?utm_source=feed");

        JobIngestionOutcome created = service.ingest(original, RUN_ONE);
        JobIngestionOutcome unchanged = service.ingest(original, RUN_TWO);

        assertThat(created.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(unchanged.action()).isEqualTo(JobIngestionAction.UNCHANGED);
        assertThat(unchanged.jobId()).isEqualTo(created.jobId());
        assertThat(repository.count()).isOne();
        JobPosting observed = repository.findById(created.jobId()).orElseThrow();
        assertThat(observed.lastSeenRunId()).isEqualTo(RUN_TWO);
        assertThat(observed.missingSuccessfulRunCount()).isZero();
        String firstHash = observed.contentHash();

        JobCandidate providerUpdate = provider(
                SOURCE_ONE, "provider-job-1", "Example Systems", "Backend Engineer", "Example City",
                "Provider description two", "https://jobs.example.test/openings/1");
        JobIngestionOutcome updated = service.ingest(providerUpdate, RUN_THREE);

        assertThat(updated.action()).isEqualTo(JobIngestionAction.UPDATED);
        assertThat(updated.jobId()).isEqualTo(created.jobId());
        assertThat(repository.count()).isOne();
        JobPostingResponse response = service.get(created.jobId());
        assertThat(response.descriptionPlainText()).isEqualTo("Provider description two");
        assertThat(response.contentHash()).isNotEqualTo(firstHash);
        assertThat(response.lastSeenAt()).isAfterOrEqualTo(response.firstSeenAt());
    }

    @Test
    void canonicalApplicationUrlMarksCrossSourceRecordAsPersistedDuplicate() {
        JobIngestionOutcome original = service.ingest(provider(
                SOURCE_ONE, "lever-1", "Example Systems", "Platform Engineer", "Example City", "First source",
                "HTTPS://careers.example.test:443/jobs/42/?utm_source=lever&job=42#apply"), RUN_ONE);
        JobIngestionOutcome duplicate = service.ingest(provider(
                SOURCE_TWO, "greenhouse-9", "Different Display Company", "Different feed title", "Remote",
                "Second source", "https://careers.example.test/jobs/42?job=42&utm_campaign=greenhouse"), RUN_TWO);

        assertThat(original.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(duplicate.action()).isEqualTo(JobIngestionAction.DUPLICATE);
        JobPostingResponse duplicateRecord = service.get(duplicate.jobId());
        assertThat(duplicateRecord.status()).isEqualTo(JobPostingStatus.DUPLICATE);
        assertThat(duplicateRecord.duplicateOfJobId()).isEqualTo(original.jobId());
        assertThat(repository.count()).isEqualTo(2);
        assertThat(service.duplicates(original.jobId()))
                .extracting(JobPostingResponse::id)
                .containsExactly(duplicate.jobId());
    }

    @Test
    void deterministicFingerprintMarksExactStableIdentityAsDuplicate() {
        JobIngestionOutcome original = service.ingest(provider(
                SOURCE_ONE, "lever-fingerprint", "Example Systems", "Reliability Engineer", "Example City",
                "Original description", null), RUN_ONE);
        JobIngestionOutcome duplicate = service.ingest(provider(
                SOURCE_TWO, "greenhouse-fingerprint", "Example Systems", "Reliability Engineer", "Example City",
                "Different provider description", null), RUN_TWO);

        assertThat(original.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(duplicate.action()).isEqualTo(JobIngestionAction.DUPLICATE);
        assertThat(service.get(duplicate.jobId()).duplicateOfJobId()).isEqualTo(original.jobId());
    }

    @Test
    void similarTitleWithDifferentCompanyOrLocationRemainsSeparate() {
        JobIngestionOutcome baseline = service.ingest(provider(
                SOURCE_ONE, "base", "Example Systems", "Software Engineer", "City One", "Description", null),
                RUN_ONE);
        JobIngestionOutcome otherCompany = service.ingest(provider(
                SOURCE_TWO, "company", "Another Systems", "Software Engineer", "City One", "Description", null),
                RUN_TWO);
        JobIngestionOutcome otherLocation = service.ingest(provider(
                SOURCE_TWO, "location", "Example Systems", "Software Engineer", "City Two", "Description", null),
                RUN_TWO);

        assertThat(baseline.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(otherCompany.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(otherLocation.action()).isEqualTo(JobIngestionAction.CREATED);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(service.get(otherCompany.jobId()).duplicateOfJobId()).isNull();
        assertThat(service.get(otherLocation.jobId()).duplicateOfJobId()).isNull();
    }

    @Test
    void manualIdempotencyKeyReturnsOriginalRecordWithoutCreatingAnother() {
        ManualJobRequest firstRequest = manual(
                "Example Systems", "Manual Backend Engineer", "Initial manually entered description",
                "https://careers.example.test/jobs/manual-1?utm_source=manual");
        ManualJobRequest replayWithDifferentBody = manual(
                "Changed Replay Company", "Changed replay title", "Changed replay description",
                "https://other.example.test/jobs/different");

        JobPostingResponse first = service.createManual(firstRequest, "manual:test-key-1");
        JobPostingResponse replay = service.createManual(replayWithDifferentBody, "manual:test-key-1");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.company()).isEqualTo(first.company());
        assertThat(replay.title()).isEqualTo(first.title());
        assertThat(repository.count()).isOne();
    }

    @Test
    void validatesManualIdempotencyKeyAndOptimisticEditVersion() {
        assertThatThrownBy(() -> service.createManual(
                manual("Example", "Engineer", "Description", null), "invalid key with spaces"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Idempotency-Key is invalid");

        JobPostingResponse created = service.createManual(
                manual("Example", "Engineer", "Description", null), "manual:test-key-2");
        assertThatThrownBy(() -> service.update(created.id(), update(
                created, "Example", "Engineer", "Edited description", created.recordVersion() + 1)))
                .isInstanceOf(ConflictException.class);

        JobPostingResponse edited = service.update(created.id(), update(
                created, "Example", "Engineer", "Edited description", created.recordVersion()));
        assertThat(edited.descriptionPlainText()).isEqualTo("Edited description");
        assertThat(edited.manuallyEdited()).isFalse();
        assertThat(edited.recordVersion()).isGreaterThan(created.recordVersion());
    }

    @Test
    void providerRefreshDoesNotOverwriteManualCorrectionAndSignalsAvailableUpdate() {
        JobCandidate providerVersionOne = provider(
                SOURCE_ONE, "protected-job", "Example Systems", "Backend Engineer", "Example City",
                "Provider description one", "https://jobs.example.test/protected/1");
        JobIngestionOutcome created = service.ingest(providerVersionOne, RUN_ONE);
        JobPostingResponse original = service.get(created.jobId());

        JobPostingResponse manuallyCorrected = service.update(created.jobId(), update(
                original, "Example Systems", "Corrected Backend Engineer", "Human-reviewed correction",
                original.recordVersion()));
        assertThat(manuallyCorrected.manuallyEdited()).isTrue();
        assertThat(manuallyCorrected.sourceUpdateAvailable()).isFalse();
        String manuallyEditedHash = manuallyCorrected.contentHash();

        JobCandidate providerVersionTwo = provider(
                SOURCE_ONE, "protected-job", "Example Systems", "Backend Engineer", "Example City",
                "Provider description two", "https://jobs.example.test/protected/1");
        JobIngestionOutcome providerUpdate = service.ingest(providerVersionTwo, RUN_TWO);
        JobPostingResponse retained = service.get(created.jobId());

        assertThat(providerUpdate.action()).isEqualTo(JobIngestionAction.UPDATED);
        assertThat(retained.title()).isEqualTo("Corrected Backend Engineer");
        assertThat(retained.descriptionPlainText()).isEqualTo("Human-reviewed correction");
        assertThat(retained.contentHash()).isEqualTo(manuallyEditedHash);
        assertThat(retained.manuallyEdited()).isTrue();
        assertThat(retained.sourceUpdateAvailable()).isTrue();

        JobIngestionOutcome repeatedProviderUpdate = service.ingest(providerVersionTwo, RUN_THREE);
        assertThat(repeatedProviderUpdate.action()).isEqualTo(JobIngestionAction.UNCHANGED);
        assertThat(service.get(created.jobId()).sourceUpdateAvailable()).isTrue();
    }

    @Test
    void missingThresholdIgnoresSeenJobAndReappearanceRestoresSourceRemovedJob() {
        JobCandidate missingCandidate = provider(
                SOURCE_ONE, "missing-job", "Example Systems", "Backend Engineer", "City One",
                "Missing candidate", "https://jobs.example.test/missing");
        JobCandidate presentCandidate = provider(
                SOURCE_ONE, "present-job", "Example Systems", "Platform Engineer", "City Two",
                "Present candidate", "https://jobs.example.test/present");
        UUID missingId = service.ingest(missingCandidate, RUN_ONE).jobId();
        UUID presentId = service.ingest(presentCandidate, RUN_ONE).jobId();

        service.ingest(presentCandidate, RUN_TWO);
        MissingJobResult firstMissingRun = service.markMissingAfterSuccessfulRun(SOURCE_ONE, RUN_TWO, 2);
        assertThat(firstMissingRun.removedCount()).isZero();
        assertThat(service.get(missingId).missingSuccessfulRunCount()).isOne();
        assertThat(service.get(missingId).status()).isEqualTo(JobPostingStatus.READY_FOR_EVALUATION);
        assertThat(service.get(presentId).missingSuccessfulRunCount()).isZero();

        service.ingest(presentCandidate, RUN_THREE);
        MissingJobResult secondMissingRun = service.markMissingAfterSuccessfulRun(SOURCE_ONE, RUN_THREE, 2);
        assertThat(secondMissingRun.removedCount()).isOne();
        assertThat(service.get(missingId).missingSuccessfulRunCount()).isEqualTo(2);
        assertThat(service.get(missingId).status()).isEqualTo(JobPostingStatus.SOURCE_REMOVED);
        assertThat(service.get(presentId).missingSuccessfulRunCount()).isZero();

        JobIngestionOutcome reappeared = service.ingest(missingCandidate, RUN_FOUR);
        JobPostingResponse restored = service.get(missingId);
        assertThat(reappeared.action()).isEqualTo(JobIngestionAction.UNCHANGED);
        assertThat(restored.status()).isEqualTo(JobPostingStatus.READY_FOR_EVALUATION);
        assertThat(restored.missingSuccessfulRunCount()).isZero();
        assertThat(repository.findById(missingId).orElseThrow().lastSeenRunId()).isEqualTo(RUN_FOUR);
    }

    @Test
    void archivedProviderJobRemainsArchivedWhenSeenAgain() {
        JobCandidate candidate = provider(
                SOURCE_ONE, "archived-job", "Example Systems", "Engineer", "Example City",
                "Provider description", "https://jobs.example.test/archived");
        UUID id = service.ingest(candidate, RUN_ONE).jobId();
        service.archive(id);

        service.ingest(candidate, RUN_TWO);

        JobPostingResponse archived = service.get(id);
        assertThat(archived.status()).isEqualTo(JobPostingStatus.ARCHIVED);
        assertThat(archived.missingSuccessfulRunCount()).isZero();
    }

    private static JobCandidate provider(
            UUID sourceId,
            String externalId,
            String company,
            String title,
            String location,
            String description,
            String applyUrl) {
        return new JobCandidate(
                sourceId,
                JobSourceType.LEVER,
                externalId,
                company,
                title,
                location,
                "US",
                WorkplaceType.HYBRID,
                EmploymentType.FULL_TIME,
                "Engineering",
                "Platform",
                description,
                false,
                applyUrl,
                applyUrl,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(140_000),
                "USD",
                SalaryInterval.YEAR,
                Instant.parse("2026-08-20T08:00:00Z"),
                Instant.parse("2026-08-21T08:00:00Z"),
                null);
    }

    private static ManualJobRequest manual(String company, String title, String description, String applyUrl) {
        return new ManualJobRequest(
                company,
                title,
                "Example City",
                "US",
                WorkplaceType.REMOTE,
                EmploymentType.FULL_TIME,
                "Engineering",
                "Platform",
                description,
                applyUrl,
                applyUrl,
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-20T08:00:00Z"),
                null);
    }

    private static JobUpdateRequest update(
            JobPostingResponse existing,
            String company,
            String title,
            String description,
            long version) {
        return new JobUpdateRequest(
                company,
                title,
                existing.location(),
                existing.countryCode(),
                existing.workplaceType(),
                existing.employmentType(),
                existing.department(),
                existing.team(),
                description,
                existing.applyUrl(),
                existing.sourceUrl(),
                existing.salaryMinimum(),
                existing.salaryMaximum(),
                existing.salaryCurrency(),
                existing.salaryInterval(),
                existing.publishedAt(),
                existing.expiresAt(),
                version);
    }
}
