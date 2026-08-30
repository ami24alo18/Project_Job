package com.amit.jobagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.common.error.DomainValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNormalizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void normalizesUnicodeWhitespaceEnumsCurrencyCountryAndUrls() {
        JobCandidate candidate = candidate(
                "  Example\u0007  Systems  ",
                "Cafe\u0301\tPlatform   Engineer",
                "  Example   City ",
                "Builds\u0000 reliable\tservices.",
                false,
                "HTTPS://Jobs.Example.TEST:443/openings/42/?utm_source=alert&job=42#apply");

        NormalizedJob normalized = service(1_000).normalize(new JobCandidate(
                candidate.sourceId(), candidate.sourceType(), candidate.externalId(), candidate.company(),
                candidate.title(), candidate.location(), "us", null, null, " Engineering ", " Core\tTeam ",
                candidate.description(), candidate.descriptionHtml(), candidate.applyUrl(),
                "https://SOURCE.example.test/job/42/#details", BigDecimal.valueOf(100),
                BigDecimal.valueOf(200), " usd ", null, candidate.publishedAt(), candidate.sourceUpdatedAt(),
                candidate.expiresAt()));

        assertThat(normalized.company()).isEqualTo("Example Systems");
        assertThat(normalized.title()).isEqualTo("Café Platform Engineer");
        assertThat(normalized.location()).isEqualTo("Example City");
        assertThat(normalized.department()).isEqualTo("Engineering");
        assertThat(normalized.team()).isEqualTo("Core Team");
        assertThat(normalized.descriptionPlainText()).isEqualTo("Builds reliable services.");
        assertThat(normalized.countryCode()).isEqualTo("US");
        assertThat(normalized.salaryCurrency()).isEqualTo("USD");
        assertThat(normalized.workplaceType()).isEqualTo(WorkplaceType.UNSPECIFIED);
        assertThat(normalized.employmentType()).isEqualTo(EmploymentType.UNSPECIFIED);
        assertThat(normalized.salaryInterval()).isEqualTo(SalaryInterval.UNSPECIFIED);
        assertThat(normalized.applyUrl()).isEqualTo("https://jobs.example.test/openings/42?job=42");
        assertThat(normalized.canonicalApplyUrl()).isEqualTo(normalized.applyUrl());
        assertThat(normalized.sourceUrl()).isEqualTo("https://source.example.test/job/42");
        assertThat(normalized.initialStatus()).isEqualTo(JobPostingStatus.READY_FOR_EVALUATION);
    }

    @Test
    void convertsUntrustedHtmlToPlainTextAndRemovesExecutableContent() {
        String html = """
                <style>.hidden { display:none }</style>
                <script>stealCredentials()</script>
                <p>Hello&nbsp;&nbsp;fictional candidate</p>
                <iframe>unsafe frame text</iframe>
                <object>unsafe object text</object>
                <p>Build\u0007 safe services</p>
                """;

        NormalizedJob normalized = service(1_000).normalize(candidate(
                "Example Systems", "Backend Engineer", "Remote", html, true, null));

        assertThat(normalized.descriptionPlainText())
                .isEqualTo("Hello fictional candidate Build safe services")
                .doesNotContain("script", "style", "iframe", "object", "stealCredentials", "<", ">");
        assertThat(normalized.descriptionTruncated()).isFalse();
    }

    @Test
    void decodesEntityEncodedProviderHtmlBeforeRemovingMarkupAndExecutableContent() {
        String encoded = "&lt;p&gt;Build safe services.&lt;/p&gt;"
                + "&lt;script&gt;stealCredentials()&lt;/script&gt;"
                + "&lt;ul&gt;&lt;li&gt;Review changes&lt;/li&gt;&lt;/ul&gt;";

        NormalizedJob normalized = service(1_000).normalize(candidate(
                "Example Systems", "Backend Engineer", "Remote", encoded, true, null));

        assertThat(normalized.descriptionPlainText())
                .isEqualTo("Build safe services. Review changes")
                .doesNotContain("script", "stealCredentials", "<", ">");
    }

    @Test
    void enforcesDescriptionLimitAndMarksTruncation() {
        NormalizedJob normalized = service(12).normalize(candidate(
                "Example Systems", "Backend Engineer", "Remote",
                "1234567890ABCDEFGHIJ", false, null));

        assertThat(normalized.descriptionPlainText()).isEqualTo("1234567890AB");
        assertThat(normalized.descriptionPlainText()).hasSize(12);
        assertThat(normalized.descriptionTruncated()).isTrue();
    }

    @Test
    void truncatesByUnicodeCodePointWithoutSplittingSurrogatePairs() {
        NormalizedJob normalized = service(2).normalize(candidate(
                "Example Systems", "Backend Engineer", "Remote", "A😀BC", false, null));

        assertThat(normalized.descriptionPlainText()).isEqualTo("A😀");
        assertThat(normalized.descriptionPlainText().codePointCount(
                0, normalized.descriptionPlainText().length())).isEqualTo(2);
        assertThat(normalized.descriptionTruncated()).isTrue();
    }

    @Test
    void usesUnknownInsteadOfGuessingInvalidCountryOrCurrency() {
        JobCandidate base = candidate("Example Systems", "Backend Engineer", null, "Description", false, null);
        NormalizedJob normalized = service(1_000).normalize(new JobCandidate(
                base.sourceId(), base.sourceType(), base.externalId(), base.company(), base.title(), base.location(),
                "United States", base.workplaceType(), base.employmentType(), null, null, base.description(), false,
                null, null, null, null, "US Dollars", null, null, null, null));

        assertThat(normalized.countryCode()).isNull();
        assertThat(normalized.salaryCurrency()).isNull();
        assertThat(normalized.location()).isNull();
    }

    @Test
    void rejectsMissingIdentityInvalidSalaryAndIncompleteManualJob() {
        assertThatThrownBy(() -> service(1_000).normalize(candidate(
                "  ", "Backend Engineer", null, "Description", false, null)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Company and title are required");

        JobCandidate invalidSalary = new JobCandidate(
                SOURCE_ID, JobSourceType.LEVER, "job-1", "Example", "Engineer", null, null,
                null, null, null, null, "Description", false, null, null,
                BigDecimal.valueOf(200), BigDecimal.valueOf(100), "USD", SalaryInterval.YEAR,
                null, null, null);
        assertThatThrownBy(() -> service(1_000).normalize(invalidSalary))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Salary range is invalid");

        JobCandidate incompleteManual = new JobCandidate(
                null, JobSourceType.MANUAL, "manual-1", "Example", "Engineer", null, null,
                null, null, null, null, null, false, null, null,
                null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service(1_000).normalize(incompleteManual))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("At least one of description or apply URL is required");
    }

    @Test
    void marksOnlyTrustedPastExpiryAsExpired() {
        JobCandidate base = candidate("Example Systems", "Engineer", null, "Description", false, null);
        JobCandidate expired = new JobCandidate(
                base.sourceId(), base.sourceType(), base.externalId(), base.company(), base.title(), null, null,
                null, null, null, null, base.description(), false, null, null,
                null, null, null, null, null, null, NOW.minusSeconds(1));
        JobCandidate future = new JobCandidate(
                base.sourceId(), base.sourceType(), "job-2", base.company(), base.title(), null, null,
                null, null, null, null, base.description(), false, null, null,
                null, null, null, null, null, null, NOW.plusSeconds(1));

        assertThat(service(1_000).normalize(expired).initialStatus()).isEqualTo(JobPostingStatus.EXPIRED);
        assertThat(service(1_000).normalize(future).initialStatus()).isEqualTo(JobPostingStatus.READY_FOR_EVALUATION);
    }

    @Test
    void producesStableSha256FingerprintAndSeparateContentHash() {
        NormalizedJob first = service(1_000).normalize(candidate(
                "Example Systems", "Backend Engineer", "Example City", "Version one", false,
                "https://jobs.example.test/openings/42/?utm_source=one&job=42"));
        NormalizedJob trackingVariant = service(1_000).normalize(candidate(
                "Example Systems", "Backend Engineer", "Example City", "Version one", false,
                "https://JOBS.example.test:443/openings/42?job=42&utm_campaign=two#apply"));
        NormalizedJob contentUpdate = service(1_000).normalize(candidate(
                "Example Systems", "Backend Engineer", "Example City", "Version two", false,
                "https://jobs.example.test/openings/42?job=42"));

        assertThat(first.fingerprint()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(trackingVariant.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(trackingVariant.contentHash()).isEqualTo(first.contentHash());
        assertThat(contentUpdate.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(contentUpdate.contentHash()).isNotEqualTo(first.contentHash());
    }

    @Test
    void fingerprintSeparatesDifferentCompanyAndLocation() {
        NormalizedJob baseline = service(1_000).normalize(candidate(
                "Example Systems", "Engineer", "City One", "Description", false, null));
        NormalizedJob otherCompany = service(1_000).normalize(candidate(
                "Another Systems", "Engineer", "City One", "Description", false, null));
        NormalizedJob otherLocation = service(1_000).normalize(candidate(
                "Example Systems", "Engineer", "City Two", "Description", false, null));

        assertThat(otherCompany.fingerprint()).isNotEqualTo(baseline.fingerprint());
        assertThat(otherLocation.fingerprint()).isNotEqualTo(baseline.fingerprint());
    }

    @Test
    void preservesExplicitExternalIngestionProvenance() {
        UUID eventId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        JobCandidate external = new JobCandidate(
                SOURCE_ID, JobSourceType.EXTERNAL_API, "jsearch-42", "Example Systems", "Backend Engineer",
                "Remote", "IN", WorkplaceType.REMOTE, EmploymentType.FULL_TIME, null, null,
                "Build services", false, "https://example.test/jobs/42", "https://linkedin.com/jobs/view/42",
                null, null, null, null, NOW, NOW, null, JobIngestionProvider.JSEARCH, "LinkedIn",
                "Java backend developer in India", eventId, null);

        NormalizedJob normalized = service(1_000).normalize(external);

        assertThat(normalized.ingestionProvider()).isEqualTo(JobIngestionProvider.JSEARCH);
        assertThat(normalized.originPublisher()).isEqualTo("LinkedIn");
        assertThat(normalized.discoveryQuery()).isEqualTo("Java backend developer in India");
        assertThat(normalized.externalEventId()).isEqualTo(eventId);
    }

    @Test
    void requiresProviderForExternalAndCareerSources() {
        JobCandidate missingProvider = new JobCandidate(
                SOURCE_ID, JobSourceType.EXTERNAL_API, "external-1", "Example", "Engineer", null, null,
                null, null, null, null, "Description", false, null, null, null, null, null, null,
                null, null, null);

        assertThatThrownBy(() -> service(1_000).normalize(missingProvider))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Ingestion provider is required for this source type");
    }

    private static JobNormalizationService service(int maximumDescriptionCharacters) {
        JobIngestionProperties properties = new JobIngestionProperties(
                maximumDescriptionCharacters, 1_000_000, 500, 1_000, 2, 10, 1, 1, 10, 5);
        return new JobNormalizationService(
                new JobUrlCanonicalizer(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JobCandidate candidate(
            String company,
            String title,
            String location,
            String description,
            boolean descriptionHtml,
            String applyUrl) {
        return new JobCandidate(
                SOURCE_ID,
                JobSourceType.LEVER,
                "job-1",
                company,
                title,
                location,
                null,
                WorkplaceType.HYBRID,
                EmploymentType.FULL_TIME,
                null,
                null,
                description,
                descriptionHtml,
                applyUrl,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
