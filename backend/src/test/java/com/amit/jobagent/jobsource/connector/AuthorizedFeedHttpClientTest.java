package com.amit.jobagent.jobsource.connector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AuthorizedFeedHttpClientTest extends ConnectorWireMockSupport {
    @Test
    void honorsRetryAfterForRateLimitThenSucceeds() {
        var sleeps = new ArrayList<Duration>();
        server.stubFor(get(urlEqualTo("/rate-limited"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("available"));
        server.stubFor(get(urlEqualTo("/rate-limited"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("available")
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        byte[] result = http(1_000, 2, Duration.ofSeconds(2), sleeps)
                .get(URI.create(baseUrl() + "/rate-limited"));

        assertThat(new String(result)).isEqualTo("[]");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        server.verify(2, getRequestedFor(urlEqualTo("/rate-limited")));
    }

    @Test
    void doesNotRetryPermanentClientFailure() {
        server.stubFor(get(urlEqualTo("/missing")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> http().get(URI.create(baseUrl() + "/missing")))
                .isInstanceOf(SourceFetchException.class)
                .satisfies(error -> {
                    SourceFetchException failure = (SourceFetchException) error;
                    assertThat(failure.code()).isEqualTo(SourceFetchErrorCode.PERMANENT_HTTP_ERROR);
                    assertThat(failure.httpStatus()).isEqualTo(404);
                    assertThat(failure.retryable()).isFalse();
                });
        server.verify(1, getRequestedFor(urlEqualTo("/missing")));
    }

    @Test
    void distinguishesExhaustedRateLimitAndServerFailure() {
        server.stubFor(get(urlEqualTo("/always-rate-limited"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));
        assertThatThrownBy(() -> http(1_000, 2, Duration.ofSeconds(2), new ArrayList<>())
                        .get(URI.create(baseUrl() + "/always-rate-limited")))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.RATE_LIMITED);

        server.stubFor(get(urlEqualTo("/always-failing")).willReturn(aResponse().withStatus(500)));
        var serverFailureSleeps = new ArrayList<Duration>();
        assertThatThrownBy(() -> http(1_000, 2, Duration.ofSeconds(2), serverFailureSleeps)
                        .get(URI.create(baseUrl() + "/always-failing")))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.RETRYABLE_HTTP_ERROR);
        assertThat(serverFailureSleeps).containsExactly(Duration.ofMillis(10));
        server.verify(2, getRequestedFor(urlEqualTo("/always-failing")));
    }

    @Test
    void enforcesResponseSizeWithoutRetainingBody() {
        server.stubFor(get(urlEqualTo("/large"))
                .willReturn(aResponse().withStatus(200).withBody("0123456789")));

        assertThatThrownBy(() -> http(5, 1, Duration.ofSeconds(2), new ArrayList<>())
                        .get(URI.create(baseUrl() + "/large")))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.RESPONSE_TOO_LARGE);
    }

    @Test
    void disablesRedirectFollowing() {
        server.stubFor(get(urlEqualTo("/redirect"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl() + "/elsewhere")));
        server.stubFor(get(urlEqualTo("/elsewhere")).willReturn(aResponse().withStatus(200).withBody("[]")));

        assertThatThrownBy(() -> http().get(URI.create(baseUrl() + "/redirect")))
                .isInstanceOf(SourceFetchException.class)
                .extracting(error -> ((SourceFetchException) error).code())
                .isEqualTo(SourceFetchErrorCode.REDIRECT_REJECTED);
        server.verify(0, getRequestedFor(urlEqualTo("/elsewhere")));
    }

    @Test
    void reportsTimeoutAfterBoundedRetries() {
        server.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(250).withBody("[]")));

        assertThatThrownBy(() -> http(1_000, 2, Duration.ofMillis(50), new ArrayList<>())
                        .get(URI.create(baseUrl() + "/slow")))
                .isInstanceOf(SourceFetchException.class)
                .satisfies(error -> {
                    SourceFetchException failure = (SourceFetchException) error;
                    assertThat(failure.code()).isEqualTo(SourceFetchErrorCode.TIMEOUT);
                    assertThat(failure.retryable()).isTrue();
                });
        server.verify(2, getRequestedFor(urlEqualTo("/slow")));
    }
}
