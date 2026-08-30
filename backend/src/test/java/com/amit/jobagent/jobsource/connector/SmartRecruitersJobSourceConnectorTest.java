package com.amit.jobagent.jobsource.connector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SmartRecruitersJobSourceConnectorTest extends ConnectorWireMockSupport {
    @Test
    void fetchesCompletePublicInventoryWithBoundedOffsetPagination() {
        server.stubFor(get(urlPathEqualTo("/smart/ExampleCompany/postings"))
                .withQueryParam("destination", equalTo("PUBLIC"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("limit", equalTo("2"))
                .willReturn(json("""
                        {"offset":0,"limit":2,"totalFound":3,"content":[
                          {"id":"101","name":"Platform Engineer","releasedDate":"2026-08-01T10:00:00Z",
                           "company":{"name":"Example Co"},"location":{"city":"Pune","region":"MH","country":"in","remote":true},
                           "department":{"label":"Engineering"},"typeOfEmployment":{"label":"Full-time"}},
                          {"id":"102","name":"Backend Engineer"}]}
                        """)));
        server.stubFor(get(urlPathEqualTo("/smart/ExampleCompany/postings"))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(json("""
                        {"offset":2,"limit":2,"totalFound":3,"content":[{"id":"103","name":"Java Engineer"}]}
                        """)));

        SourceFetchResult result = connector().fetch(request(2, 3));

        assertThat(result.complete()).isTrue();
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.records()).extracting(RawJobRecord::externalId).containsExactly("101", "102", "103");
        RawJobRecord first = result.records().getFirst();
        assertThat(first.location()).isEqualTo("Pune, MH, in");
        assertThat(first.workplaceType()).isEqualTo("REMOTE");
        assertThat(first.applyUrl()).isEqualTo("https://jobs.smartrecruiters.com/ExampleCompany/101");
    }

    @Test
    void pageCapProducesPartialInventoryWithoutClaimingRemovalSafety() {
        server.stubFor(get(urlPathEqualTo("/smart/ExampleCompany/postings"))
                .willReturn(json("{" + "\"offset\":0,\"limit\":1,\"totalFound\":2,\"content\":[{\"id\":\"1\",\"name\":\"One\"}]}")));

        SourceFetchResult result = connector().fetch(request(1, 1));

        assertThat(result.complete()).isFalse();
        assertThat(result.nextCheckpoint().value()).isEqualTo("1");
    }

    private SmartRecruitersJobSourceConnector connector() {
        var endpoints = new ProviderEndpointResolver(URI.create(baseUrl()+"/lever/"),
                URI.create(baseUrl()+"/lever-eu/"), URI.create(baseUrl()+"/greenhouse/"),
                URI.create(baseUrl()+"/smart/"));
        return new SmartRecruitersJobSourceConnector(mapper, endpoints, http());
    }

    private static SourceFetchRequest request(int pageSize, int maximumPages) {
        return new SourceFetchRequest(JobSourceType.CAREER_SITE, JobSourceConnectorType.SMARTRECRUITERS,
                "ExampleCompany", SourceRegion.DEFAULT, "https://jobs.smartrecruiters.com/ExampleCompany",
                "jobs.smartrecruiters.com", pageSize, maximumPages, SourceCheckpoint.beginning());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
