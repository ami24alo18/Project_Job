package com.amit.jobagent.jobsource.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.discovery.CareerSitePage;
import com.amit.jobagent.jobsource.discovery.CareerSitePageFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GenericJsonLdJobSourceConnectorTest {
    private final CareerSitePageFetcher pages = mock(CareerSitePageFetcher.class);
    private final GenericJsonLdJobSourceConnector connector = new GenericJsonLdJobSourceConnector(new ObjectMapper(), pages);

    @Test
    void mapsOneSpecificJobPageAndKeepsItFilteredCoverageCompatible() {
        when(pages.fetch("https://careers.example.com/jobs/123")).thenReturn(new CareerSitePage(
                "https://careers.example.com/jobs/123", "text/html", """
                <html><script type="application/ld+json">{
                  "@context":"https://schema.org","@type":"JobPosting","identifier":{"value":"REQ-123"},
                  "title":"Senior Java Engineer","description":"<p>Build APIs</p>","datePosted":"2026-08-01",
                  "validThrough":"2026-09-01T00:00:00Z","employmentType":"FULL_TIME",
                  "hiringOrganization":{"name":"Example Co"},
                  "jobLocation":{"address":{"addressLocality":"Pune","addressRegion":"MH","addressCountry":"IN"}},
                  "url":"/jobs/123"
                }</script></html>
                """));

        SourceFetchResult result = connector.fetch(request());

        assertThat(result.complete()).isTrue();
        assertThat(result.records()).hasSize(1);
        RawJobRecord job = result.records().getFirst();
        assertThat(job.externalId()).isEqualTo("REQ-123");
        assertThat(job.company()).isEqualTo("Example Co");
        assertThat(job.location()).isEqualTo("Pune, MH, IN");
        assertThat(job.descriptionHtml()).contains("Build APIs");
        assertThat(job.sourceUrl()).isEqualTo("https://careers.example.com/jobs/123");
    }

    @Test
    void rejectsPageWhenStructuredJobDataDisappears() {
        when(pages.fetch("https://careers.example.com/jobs/123")).thenReturn(new CareerSitePage(
                "https://careers.example.com/jobs/123", "text/html", "<html><body>Gone</body></html>"));

        assertThatThrownBy(() -> connector.fetch(request()))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.INVALID_PAYLOAD);
    }

    private static SourceFetchRequest request() {
        return new SourceFetchRequest(JobSourceType.CAREER_SITE, JobSourceConnectorType.GENERIC_JSON_LD,
                "careers.example.com", SourceRegion.DEFAULT, "https://careers.example.com/jobs/123",
                "careers.example.com", 10, 1, SourceCheckpoint.beginning());
    }
}
