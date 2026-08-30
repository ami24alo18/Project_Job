package com.amit.jobagent.jobsource;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "job-agent.ingestion.maximum-external-event-bytes=4096"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExternalIngestionApiIntegrationTest {
    private static final String USER = "test-user";
    private static final String PASSWORD = "test-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void createsRulesIngestsWithProvenanceAndReplaysDeterministically() throws Exception {
        JsonNode created = createExternalSource("JSearch India", "jsearch-india");
        String sourceId = created.path("source").path("id").asText();
        String token = created.path("webhookToken").asText();

        org.assertj.core.api.Assertions.assertThat(token).hasSize(43);
        mvc.perform(get("/api/v1/job-sources/{id}", sourceId).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("EXTERNAL_API"))
                .andExpect(jsonPath("$.sourceCategory").value("PUSH_WEBHOOK"))
                .andExpect(jsonPath("$.connectorType").value("JSEARCH"))
                .andExpect(jsonPath("$.webhookConfigured").value(true))
                .andExpect(jsonPath("$.webhookToken").doesNotExist());

        JsonNode rule = body(mvc.perform(post("/api/v1/job-sources/{sourceId}/search-rules", sourceId)
                        .with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchRuleJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Java backend - India"))
                .andExpect(jsonPath("$.query").value("Java Backend Developer"))
                .andExpect(jsonPath("$.locations", hasSize(2)))
                .andExpect(jsonPath("$.locations[0]").value("India"))
                .andExpect(jsonPath("$.maximumResults").value(100))
                .andReturn().getResponse().getContentAsString());

        String eventId = "n8n-jsearch-india-20260825-1";
        String request = eventJson(eventId, rule.path("id").asText(), false);
        JsonNode first = body(mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.status").value("PARTIAL_SUCCESS"))
                .andExpect(jsonPath("$.discoveredCount").value(2))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].action").value("CREATED"))
                .andExpect(jsonPath("$.results[1].errorCode").value("INVALID_JOB"))
                .andExpect(jsonPath("$.results[1].errorMessage")
                        .value("The mapped job could not be normalized or stored"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/job-source-runs/{runId}", first.path("runId").asText())
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerType").value("WEBHOOK"))
                .andExpect(jsonPath("$.coverage").value("PUSH_BATCH"))
                .andExpect(jsonPath("$.searchRuleId").value(rule.path("id").asText()))
                .andExpect(jsonPath("$.removedCount").value(0));

        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD))
                        .param("sourceId", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ingestionProvider").value("JSEARCH"))
                .andExpect(jsonPath("$.content[0].originPublisher").value("LINKEDIN"))
                .andExpect(jsonPath("$.content[0].discoveryQuery").value("Java Backend Developer"))
                .andExpect(jsonPath("$.content[0].externalEventId", matchesPattern("[0-9a-f-]{36}")));

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(first.path("runId").asText()))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.createdCount").value(1));

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("Senior Java Backend Developer", "Changed replay title")))
                .andExpect(status().isConflict());
    }

    @Test
    void sourceScopedTokenIsRequiredAndRotationImmediatelyInvalidatesTheOldToken() throws Exception {
        JsonNode created = createExternalSource("Rotating JSearch", "rotating-jsearch");
        String sourceId = created.path("source").path("id").asText();
        String oldToken = created.path("webhookToken").asText();
        String eventId = "rotation-event-1";
        String request = oneJobEventJson(eventId);

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .with(httpBasic(USER, PASSWORD))
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Webhook authentication failed"));
        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", UUID.randomUUID())
                        .header("X-Job-Agent-Webhook-Token", oldToken)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Webhook authentication failed"));

        JsonNode rotated = body(mvc.perform(post("/api/v1/job-sources/{sourceId}/rotate-token", sourceId)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(sourceId))
                .andReturn().getResponse().getContentAsString());
        String newToken = rotated.path("webhookToken").asText();
        org.assertj.core.api.Assertions.assertThat(newToken).isNotEqualTo(oldToken);

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", oldToken)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", newToken)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void rejectsMismatchedIdempotencyAndProviderWithoutIngesting() throws Exception {
        JsonNode created = createExternalSource("Strict JSearch", "strict-jsearch");
        String sourceId = created.path("source").path("id").asText();
        String token = created.path("webhookToken").asText();
        String eventId = "strict-event-1";
        String request = oneJobEventJson(eventId);

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", "different-event")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("JSEARCH", "CUSTOM_WEBHOOK")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsExternalEventBodiesAboveTheConfiguredBound() throws Exception {
        JsonNode created = createExternalSource("Bounded JSearch", "bounded-jsearch");
        String sourceId = created.path("source").path("id").asText();
        String token = created.path("webhookToken").asText();
        String eventId = "oversized-event-1";
        var root = eventRoot(eventId);
        root.put("query", "Java backend");
        root.withArray("jobs").add(validJob("oversized-job").put("description", "x".repeat(5000)));

        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(root)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("Payload too large"));
    }

    @Test
    void jobSpyIsAFirstClassReplaySafeFilteredExternalProvider() throws Exception {
        JsonNode created = createExternalSource("JobSpy India", "jobspy-india", "JOBSPY");
        String sourceId = created.path("source").path("id").asText();
        String token = created.path("webhookToken").asText();
        String eventId = "n8n-jobspy-india-20260829-1";
        var root = eventRoot(eventId, "JOBSPY");
        root.put("query", "Java Engineer in India");
        root.withArray("jobs").add(validJob("in-jobspy-101").put("originPublisher", "INDEED"));
        String request = mapper.writeValueAsString(root);

        JsonNode response = body(mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/job-source-runs/{runId}", response.path("runId").asText())
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage").value("PUSH_BATCH"))
                .andExpect(jsonPath("$.removedCount").value(0));
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD)).param("sourceId", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ingestionProvider").value("JOBSPY"))
                .andExpect(jsonPath("$.content[0].originPublisher").value("INDEED"));
        mvc.perform(post("/api/v1/job-sources/{sourceId}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
    }

    private JsonNode createExternalSource(String displayName, String identifier) throws Exception {
        return createExternalSource(displayName, identifier, "JSEARCH");
    }

    private JsonNode createExternalSource(String displayName, String identifier, String connector) throws Exception {
        var request = mapper.createObjectNode()
                .put("displayName", displayName)
                .put("providerIdentifier", identifier)
                .put("connectorType", connector)
                .put("enabled", true);
        return body(mvc.perform(post("/api/v1/job-sources/external")
                        .with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source.sourceType").value("EXTERNAL_API"))
                .andExpect(jsonPath("$.source.connectorType").value(connector))
                .andExpect(jsonPath("$.webhookUrl", matchesPattern("/api/v1/job-sources/[0-9a-f-]{36}/external-events")))
                .andReturn().getResponse().getContentAsString());
    }

    private String searchRuleJson() throws Exception {
        var request = mapper.createObjectNode()
                .put("name", "  Java   backend - India ")
                .put("query", " Java   Backend Developer ")
                .put("remoteAllowed", true)
                .put("hybridAllowed", true)
                .put("onsiteAllowed", true)
                .put("datePostedWindow", "TODAY")
                .put("maximumResults", 100)
                .put("enabled", true);
        request.putArray("locations").add(" India ").add("india").add("Bengaluru");
        request.putNull("recordVersion");
        return mapper.writeValueAsString(request);
    }

    private String eventJson(String eventId, String ruleId, boolean onlyValid) throws Exception {
        var root = eventRoot(eventId);
        root.put("searchRuleId", ruleId);
        root.putNull("query");
        root.withArray("jobs").add(validJob("jsearch-101"));
        if (!onlyValid) {
            root.withArray("jobs").add(mapper.createObjectNode()
                    .put("externalId", "jsearch-invalid")
                    .put("title", "Missing company")
                    .put("description", "Invalid individual record"));
        }
        return mapper.writeValueAsString(root);
    }

    private String oneJobEventJson(String eventId) throws Exception {
        var root = eventRoot(eventId);
        root.put("query", "Java backend in India");
        root.withArray("jobs").add(validJob("job-" + eventId));
        return mapper.writeValueAsString(root);
    }

    private ObjectNode eventRoot(String eventId) {
        return eventRoot(eventId, "JSEARCH");
    }

    private ObjectNode eventRoot(String eventId, String provider) {
        var root = mapper.createObjectNode()
                .put("eventId", eventId)
                .put("ingestionProvider", provider)
                .put("fetchedAt", "2026-08-25T12:00:00Z");
        root.putArray("jobs");
        return root;
    }

    private ObjectNode validJob(String externalId) {
        return mapper.createObjectNode()
                .put("externalId", externalId)
                .put("originPublisher", "LINKEDIN")
                .put("company", "Example Company")
                .put("title", "Senior Java Backend Developer")
                .put("location", "Bengaluru, India")
                .put("countryCode", "IN")
                .put("workplaceType", "HYBRID")
                .put("employmentType", "FULL_TIME")
                .put("description", "Build safe and reliable backend services")
                .put("applyUrl", "https://careers.example.test/jobs/" + externalId)
                .put("sourceUrl", "https://www.linkedin.com/jobs/view/" + externalId)
                .put("salaryInterval", "UNSPECIFIED")
                .put("publishedAt", "2026-08-25T09:00:00Z");
    }

    private JsonNode body(String value) throws Exception { return mapper.readTree(value); }
}
