package com.amit.jobagent.jobsource;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.job.EmploymentType;
import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.job.WorkplaceType;
import com.amit.jobagent.jobsource.connector.RawJobRecord;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobSourceNormalizerTest {

    private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void leverMapsKnownWorkplaceAndEmploymentValues() {
        var normalizer = new LeverJobSourceNormalizer();
        var context = context(JobSourceType.LEVER, "Lever fallback");

        Map.of(
                        "remote", WorkplaceType.REMOTE,
                        "HYBRID", WorkplaceType.HYBRID,
                        "on_site", WorkplaceType.ONSITE,
                        "onsite", WorkplaceType.ONSITE)
                .forEach((rawValue, expected) ->
                        assertThat(normalizer.normalize(raw(null, rawValue, null, null, "plain", null), context)
                                        .workplaceType())
                                .as("workplace type %s", rawValue)
                                .isEqualTo(expected));

        Map.of(
                        "Full time", EmploymentType.FULL_TIME,
                        "part_time", EmploymentType.PART_TIME,
                        "contractor", EmploymentType.CONTRACT,
                        "temporary", EmploymentType.TEMPORARY,
                        "internship", EmploymentType.INTERNSHIP)
                .forEach((rawValue, expected) ->
                        assertThat(normalizer.normalize(raw(null, null, rawValue, null, "plain", null), context)
                                        .employmentType())
                                .as("employment type %s", rawValue)
                                .isEqualTo(expected));
    }

    @Test
    void leverMapsUnknownAndMissingValuesToUnspecified() {
        var normalizer = new LeverJobSourceNormalizer();
        var context = context(JobSourceType.LEVER, "Lever fallback");

        JobCandidate unknown = normalizer.normalize(
                raw(null, "distributed", "seasonal", null, "plain", null), context);
        JobCandidate missing = normalizer.normalize(raw(null, null, null, null, "plain", null), context);

        assertThat(unknown.workplaceType()).isEqualTo(WorkplaceType.UNSPECIFIED);
        assertThat(unknown.employmentType()).isEqualTo(EmploymentType.UNSPECIFIED);
        assertThat(missing.workplaceType()).isEqualTo(WorkplaceType.UNSPECIFIED);
        assertThat(missing.employmentType()).isEqualTo(EmploymentType.UNSPECIFIED);
    }

    @Test
    void greenhousePreservesHtmlFlagAndFallsBackToSourceDisplayName() {
        var normalizer = new GreenhouseJobSourceNormalizer();
        var context = context(JobSourceType.GREENHOUSE, "Greenhouse fallback");

        JobCandidate html = normalizer.normalize(
                raw(null, null, null, null, "plain ignored", "<p>Rich description</p>"), context);
        JobCandidate plain = normalizer.normalize(
                raw("Explicit company", null, null, null, "Plain description", null), context);

        assertThat(html.company()).isEqualTo("Greenhouse fallback");
        assertThat(html.description()).isEqualTo("<p>Rich description</p>");
        assertThat(html.descriptionHtml()).isTrue();
        assertThat(plain.company()).isEqualTo("Explicit company");
        assertThat(plain.description()).isEqualTo("Plain description");
        assertThat(plain.descriptionHtml()).isFalse();
    }

    private static JobSourceRunContext context(JobSourceType sourceType, String displayName) {
        return new JobSourceRunContext(
                RUN_ID, SOURCE_ID, displayName, sourceType, "provider", SourceRegion.DEFAULT, 50, 10, 3);
    }

    private static RawJobRecord raw(
            String company,
            String workplaceType,
            String employmentType,
            String countryCode,
            String descriptionPlainText,
            String descriptionHtml) {
        return new RawJobRecord(
                "external-1",
                company,
                "Software Engineer",
                "Remote",
                countryCode,
                workplaceType,
                employmentType,
                null,
                null,
                descriptionPlainText,
                descriptionHtml,
                "https://example.test/apply",
                "https://example.test/job",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
