package com.amit.jobagent.jobsource.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class ConnectorWireMockSupport {
    protected WireMockServer server;
    protected final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startWireMock() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void stopWireMock() {
        server.stop();
    }

    protected ProviderEndpointResolver endpoints() {
        return new ProviderEndpointResolver(
                URI.create(baseUrl() + "/lever-global/"),
                URI.create(baseUrl() + "/lever-eu/"),
                URI.create(baseUrl() + "/greenhouse/"));
    }

    protected AuthorizedFeedHttpClient http() {
        return http(1_000_000, 3, Duration.ofSeconds(2), new ArrayList<>());
    }

    protected String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    protected AuthorizedFeedHttpClient http(
            int maximumBytes,
            int attempts,
            Duration responseTimeout,
            List<Duration> sleeps) {
        ConnectorHttpSettings settings = new ConnectorHttpSettings(
                Duration.ofMillis(500),
                responseTimeout,
                maximumBytes,
                attempts,
                Duration.ofMillis(10),
                Duration.ofSeconds(2),
                "job-agent-connector-test");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new AuthorizedFeedHttpClient(
                client, settings, sleeps::add, () -> 0L, Clock.systemUTC());
    }

    protected static String fixture(String name) {
        try (var input = ConnectorWireMockSupport.class.getResourceAsStream("/provider-fixtures/" + name)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read fixture", ex);
        }
    }
}
