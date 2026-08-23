package com.amit.jobagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.DomainValidationException;
import org.junit.jupiter.api.Test;

class JobUrlCanonicalizerTest {
    private final JobUrlCanonicalizer canonicalizer = new JobUrlCanonicalizer();

    @Test
    void normalizesSchemeHostDefaultPortFragmentAndTrailingSlashes() {
        assertThat(canonicalizer.canonicalize(
                "  HTTPS://Jobs.Example.TEST:443/openings/backend///#application  "))
                .isEqualTo("https://jobs.example.test/openings/backend");
        assertThat(canonicalizer.canonicalize("http://EXAMPLE.test:80"))
                .isEqualTo("http://example.test/");
        assertThat(canonicalizer.canonicalize("https://example.test:8443/jobs/1/"))
                .isEqualTo("https://example.test:8443/jobs/1");
    }

    @Test
    void removesOnlyKnownTrackingParametersAndPreservesJobIdentityParameters() {
        String canonical = canonicalizer.canonicalize(
                "https://Example.test/jobs/view/?utm_source=alert&job=ABC%2F123&"
                        + "fbclid=tracking&department=R%26D&utm_Custom=value&empty=#details");

        assertThat(canonical)
                .isEqualTo("https://example.test/jobs/view?job=ABC%2F123&department=R%26D&empty=");
    }

    @Test
    void preservesRawQueryOrderAndEncodingWithoutDecodingOrSorting() {
        assertThat(canonicalizer.canonicalize(
                "https://example.test/apply?token=a%2Fb%2Bc&job=2&job=1"))
                .isEqualTo("https://example.test/apply?token=a%2Fb%2Bc&job=2&job=1");
    }

    @Test
    void hostAndPathExcludesQueryAndUsesNormalizedHost() {
        String canonical = canonicalizer.canonicalize(
                "https://CAREERS.example.test:8443/jobs/42?job=42&utm_source=test");

        assertThat(canonicalizer.hostAndPath(canonical))
                .isEqualTo("careers.example.test/jobs/42");
        assertThat(canonicalizer.hostAndPath(null)).isEmpty();
    }

    @Test
    void rejectsUnsupportedMalformedRelativeAndCredentialBearingUrls() {
        for (String invalid : new String[] {
                "file:///etc/passwd",
                "ftp://example.test/jobs/1",
                "javascript:alert(1)",
                "/relative/jobs/1",
                "https://user:secret@example.test/jobs/1",
                "https://example.test:0/jobs/1",
                "https://example.test:65536/jobs/1",
                "https://example.test:99999/jobs/1",
                "https://",
                "not a URL"
        }) {
            assertThatThrownBy(() -> canonicalizer.canonicalize(invalid))
                    .as("URL %s", invalid)
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessage("URL must be a valid HTTP or HTTPS URL");
        }
    }

    @Test
    void acceptsHighestValidExplicitPort() {
        assertThat(canonicalizer.canonicalize("https://example.test:65535/jobs/1"))
                .isEqualTo("https://example.test:65535/jobs/1");
    }

    @Test
    void treatsNullAndBlankAsUnknown() {
        assertThat(canonicalizer.canonicalize(null)).isNull();
        assertThat(canonicalizer.canonicalize("   ")).isNull();
    }
}
