package com.amit.jobagent.jobsource.connector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.job.JobSourceType;
import org.junit.jupiter.api.Test;

class LeverJobSourceConnectorTest extends ConnectorWireMockSupport {
    @Test
    void fetchesPaginatedGlobalFeedAndMapsDocumentedFields() {
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .withQueryParam("mode", equalTo("json"))
                .withQueryParam("skip", equalTo("0"))
                .withQueryParam("limit", equalTo("2"))
                .willReturn(json(fixture("lever-page-1.json"))));
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .withQueryParam("skip", equalTo("2"))
                .withQueryParam("limit", equalTo("2"))
                .willReturn(json(fixture("lever-page-2.json"))));

        SourceFetchResult result = connector().fetch(request(2, 3));

        assertThat(result.sourceType()).isEqualTo(JobSourceType.LEVER);
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.complete()).isTrue();
        assertThat(result.partial()).isFalse();
        assertThat(result.records()).hasSize(3);
        assertThat(result.recordErrors()).isEmpty();
        assertThat(result.nextCheckpoint().value()).isEqualTo("3");
        RawJobRecord first = result.records().getFirst();
        assertThat(first.externalId()).isEqualTo("lever-fictional-001");
        assertThat(first.title()).isEqualTo("Platform Engineer");
        assertThat(first.location()).isEqualTo("Example City, EX");
        assertThat(first.employmentType()).isEqualTo("Full-time");
        assertThat(first.workplaceType()).isEqualTo("hybrid");
        assertThat(first.salaryMinimum()).isEqualByComparingTo("100000");
        assertThat(first.salaryMaximum()).isEqualByComparingTo("140000");
        assertThat(first.descriptionPlainText()).isNull();
        assertThat(first.descriptionHtml()).contains("What you will do", "Build APIs", "documented public posting fields");
        assertThat(first.publishedAt()).isNull();
        assertThat(first.sourceUpdatedAt()).isNull();
        assertThat(first.expiresAt()).isNull();
        server.verify(2, getRequestedFor(urlPathEqualTo("/lever-global/fictional-company"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("job-agent-connector-test")));
    }

    @Test
    void supportsEuFeedAndEmptyResults() {
        server.stubFor(get(urlPathEqualTo("/lever-eu/fictional-company"))
                .willReturn(json("[]")));

        SourceFetchRequest request = new SourceFetchRequest(
                JobSourceType.LEVER, "fictional-company", SourceRegion.EU, 25, 2, SourceCheckpoint.beginning());
        SourceFetchResult result = connector().fetch(request);

        assertThat(result.records()).isEmpty();
        assertThat(result.pagesFetched()).isOne();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void skipsMalformedIndividualRecordWithoutDiscardingValidRecords() {
        String body = """
                [
                  {"id":"valid-id","text":"Valid fictional role","descriptionPlain":"Safe text"},
                  {"text":"Missing stable identifier","description":"<p>Not retained</p>"}
                ]
                """;
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .willReturn(json(body)));
        SourceFetchResult result = connector().fetch(request(10, 1));

        assertThat(result.records()).extracting(RawJobRecord::externalId).containsExactly("valid-id");
        assertThat(result.recordErrors()).hasSize(1);
        assertThat(result.recordErrors().getFirst().code()).isEqualTo("MALFORMED_RECORD");
        assertThat(result.partial()).isTrue();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void rejectsInvalidTopLevelPayload() {
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .willReturn(json("{\"jobs\":[]}")));

        assertThatThrownBy(() -> connector().fetch(request(10, 1)))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.INVALID_PAYLOAD);
    }

    @Test
    void returnsBoundedPartialResultWhenMaximumPageIsFull() {
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .willReturn(json(fixture("lever-page-1.json"))));

        SourceFetchResult result = connector().fetch(request(2, 1));

        assertThat(result.records()).hasSize(2);
        assertThat(result.pagesFetched()).isOne();
        assertThat(result.complete()).isFalse();
        assertThat(result.partial()).isTrue();
        assertThat(result.nextCheckpoint().value()).isEqualTo("2");
        server.verify(1, getRequestedFor(urlPathEqualTo("/lever-global/fictional-company")));
    }

    @Test
    void resumesFromNumericCheckpointAndRejectsInvalidCheckpoint() {
        server.stubFor(get(urlPathEqualTo("/lever-global/fictional-company"))
                .withQueryParam("skip", equalTo("4"))
                .willReturn(json("[]")));
        SourceFetchRequest resumed = new SourceFetchRequest(
                JobSourceType.LEVER, "fictional-company", SourceRegion.GLOBAL, 10, 1,
                new SourceCheckpoint("4"));
        assertThat(connector().fetch(resumed).nextCheckpoint().value()).isEqualTo("4");

        SourceFetchRequest invalid = new SourceFetchRequest(
                JobSourceType.LEVER, "fictional-company", SourceRegion.GLOBAL, 10, 1,
                new SourceCheckpoint("not-an-offset"));
        assertThatThrownBy(() -> connector().fetch(invalid))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.INVALID_CONFIGURATION);
    }

    private LeverJobSourceConnector connector() {
        return new LeverJobSourceConnector(mapper, endpoints(), http());
    }

    private static SourceFetchRequest request(int pageSize, int maximumPages) {
        return new SourceFetchRequest(
                JobSourceType.LEVER,
                "fictional-company",
                SourceRegion.GLOBAL,
                pageSize,
                maximumPages,
                SourceCheckpoint.beginning());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
