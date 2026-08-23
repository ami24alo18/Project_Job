package com.amit.jobagent.jobsource.connector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.job.JobSourceType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GreenhouseJobSourceConnectorTest extends ConnectorWireMockSupport {
    @Test
    void fetchesSingleUnpagedContentResponseAndMapsConservatively() {
        server.stubFor(get(urlPathEqualTo("/greenhouse/fictional-board/jobs"))
                .withQueryParam("content", equalTo("true"))
                .willReturn(json(fixture("greenhouse-jobs.json"))));

        SourceFetchResult result = connector().fetch(request());

        assertThat(result.sourceType()).isEqualTo(JobSourceType.GREENHOUSE);
        assertThat(result.pagesFetched()).isOne();
        assertThat(result.complete()).isTrue();
        assertThat(result.records()).hasSize(2);
        RawJobRecord first = result.records().getFirst();
        assertThat(first.externalId()).isEqualTo("700001");
        assertThat(first.company()).isEqualTo("Example Systems");
        assertThat(first.location()).isEqualTo("Example City");
        assertThat(first.department()).isEqualTo("Engineering");
        assertThat(first.descriptionHtml()).contains("Build safe fictional services");
        assertThat(first.descriptionPlainText()).isNull();
        assertThat(first.applyUrl()).isEqualTo(first.sourceUrl());
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-08-20T08:00:00Z"));
        assertThat(first.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-08-22T09:30:00Z"));
        assertThat(first.expiresAt()).isEqualTo(Instant.parse("2026-09-30T23:59:59Z"));
        server.verify(1, getRequestedFor(urlPathEqualTo("/greenhouse/fictional-board/jobs"))
                .withQueryParam("content", equalTo("true")));
    }

    @Test
    void handlesEmptyAndPartiallyMalformedLists() {
        server.stubFor(get(urlPathEqualTo("/greenhouse/fictional-board/jobs"))
                .willReturn(json("{\"jobs\":[]}")));
        assertThat(connector().fetch(request()).records()).isEmpty();

        server.resetAll();
        server.stubFor(get(urlPathEqualTo("/greenhouse/fictional-board/jobs"))
                .willReturn(json("{\"jobs\":[{\"id\":1,\"title\":\"Valid role\"},{\"id\":2}]}")));
        SourceFetchResult partial = connector().fetch(request());
        assertThat(partial.records()).hasSize(1);
        assertThat(partial.recordErrors()).hasSize(1);
        assertThat(partial.partial()).isTrue();
    }

    @Test
    void rejectsInvalidTopLevelResponseAndUnsupportedCheckpoint() {
        server.stubFor(get(urlPathEqualTo("/greenhouse/fictional-board/jobs"))
                .willReturn(json("[]")));
        assertThatThrownBy(() -> connector().fetch(request()))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.INVALID_PAYLOAD);

        SourceFetchRequest checkpoint = new SourceFetchRequest(
                JobSourceType.GREENHOUSE, "fictional-board", SourceRegion.DEFAULT, 50, 1,
                new SourceCheckpoint("1"));
        assertThatThrownBy(() -> connector().fetch(checkpoint))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.INVALID_CONFIGURATION);
    }

    private GreenhouseJobSourceConnector connector() {
        return new GreenhouseJobSourceConnector(mapper, endpoints(), http());
    }

    private static SourceFetchRequest request() {
        return new SourceFetchRequest(
                JobSourceType.GREENHOUSE,
                "fictional-board",
                SourceRegion.DEFAULT,
                50,
                1,
                SourceCheckpoint.beginning());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
