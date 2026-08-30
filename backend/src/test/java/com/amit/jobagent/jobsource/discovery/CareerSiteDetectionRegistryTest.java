package com.amit.jobagent.jobsource.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.JobSourceSupportStatus;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CareerSiteDetectionRegistryTest {
    private final CareerSiteDetectionRegistry detector =
            new CareerSiteDetectionRegistry(CareerSiteDiscoveryTestSupport.properties());

    @Test
    void detectsOracleCxFromVanitySitePath() {
        var response = detect("https://careers.americanexpress.com/en/sites/CX_1", "<html><body>Careers</body></html>");

        assertThat(response.connectorType()).isEqualTo(JobSourceConnectorType.ORACLE_CX);
        assertThat(response.providerIdentifier()).isEqualTo("CX_1");
        assertThat(response.supportStatus()).isEqualTo(JobSourceSupportStatus.NEEDS_AUTHORIZATION);
    }

    @Test
    void detectsOracleCxLinkFromACompanyLandingPageWithoutFollowingIt() {
        var response = detect("https://www.jpmorganchase.com/careers", """
                <html><body><a href="https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/jobs">
                Search jobs</a></body></html>
                """);

        assertThat(response.connectorType()).isEqualTo(JobSourceConnectorType.ORACLE_CX);
        assertThat(response.providerIdentifier()).isEqualTo("CX_1001");
    }

    @Test
    void detectsWorkdaySmartRecruitersAndJsonLdFingerprints() {
        var workday = detect("https://example.wd5.myworkdayjobs.com/External", "<html></html>");
        assertThat(workday.connectorType()).isEqualTo(JobSourceConnectorType.WORKDAY);
        assertThat(workday.supportStatus()).isEqualTo(JobSourceSupportStatus.NEEDS_AUTHORIZATION);

        var smartRecruiters = detect("https://jobs.smartrecruiters.com/ExampleCompany", "<html></html>");
        assertThat(smartRecruiters.connectorType()).isEqualTo(JobSourceConnectorType.SMARTRECRUITERS);
        assertThat(smartRecruiters.providerIdentifier()).isEqualTo("ExampleCompany");
        assertThat(smartRecruiters.supportStatus()).isEqualTo(JobSourceSupportStatus.SUPPORTED);

        var jsonLd = detect("https://careers.example.com/jobs/123", """
                <html><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting","title":"Engineer"}
                </script></html>
                """);
        assertThat(jsonLd.connectorType()).isEqualTo(JobSourceConnectorType.GENERIC_JSON_LD);
        assertThat(jsonLd.supportStatus()).isEqualTo(JobSourceSupportStatus.SUPPORTED);
    }

    @Test
    void routesUnknownHtmlToTheIsolatedRecipeReviewPath() {
        var response = detect("https://careers.example.com/jobs", "<html><body><div id='app'>Jobs</div></body></html>");

        assertThat(response.connectorType()).isEqualTo(JobSourceConnectorType.CUSTOM_RECIPE);
        assertThat(response.supportStatus()).isEqualTo(JobSourceSupportStatus.NEEDS_EXTRACTION_RECIPE);
        assertThat(response.detectionVersion()).isEqualTo("career-site-detection-v1");
    }

    @Test
    void boundsMarkupNodeCount() {
        var properties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_048, 20, 10_000, 3, 2, 10, 300);
        var boundedDetector = new CareerSiteDetectionRegistry(properties);

        assertThatThrownBy(() -> boundedDetector.detect(new CareerSiteDocument(
                URI.create("https://careers.example.com/jobs"), "text/html", "<html><body><p>1</p><p>2</p></body></html>")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("MARKUP_NODE_LIMIT_EXCEEDED");
    }

    private CareerSiteDiscoveryResponse detect(String url, String markup) {
        return detector.detect(new CareerSiteDocument(URI.create(url), "text/html", markup));
    }
}
