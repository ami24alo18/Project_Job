package com.amit.jobagent.jobsource;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase3JobSourceApiIntegrationTest {
    private static final String USER = "test-user";
    private static final String PASSWORD = "test-password";
    private static final String WEBHOOK_SECRET = "test-n8n-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JobSourceConfigurationService sourceService;
    @Autowired JobSourceRunPersistenceService runPersistence;
    @Autowired JobSourceRunRepository runRepository;
    @Autowired EmailIngestionEventRepository emailEvents;

    @MockBean JobSourceSyncWorker syncWorker;

    @Test
    void sourceAndRunEndpointsRequireBasicAuthentication() throws Exception {
        mvc.perform(get("/api/v1/job-sources"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/job-sources").contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Lever feed", "LEVER", "example", "GLOBAL", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/job-source-runs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/job-sources/sync-enabled"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsReadsUpdatesDisablesEnablesAndArchivesSource() throws Exception {
        var created = createSource("Example Lever", "LEVER", "example", "DEFAULT");
        var id = created.get("id").asText();

        mvc.perform(get("/api/v1/job-sources/{id}", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerIdentifier").value("example"))
                .andExpect(jsonPath("$.region").value("GLOBAL"))
                .andExpect(jsonPath("$.sourceCategory").value("PULL_FEED"))
                .andExpect(jsonPath("$.connectorType").value("LEVER"))
                .andExpect(jsonPath("$.supportStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.webhookConfigured").value(false))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.recordVersion").value(0));
        mvc.perform(get("/api/v1/job-sources").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(put("/api/v1/job-sources/{id}", id).with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Renamed Lever", "LEVER", "example", "EU", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed Lever"))
                .andExpect(jsonPath("$.region").value("EU"))
                .andExpect(jsonPath("$.recordVersion").value(1));
        mvc.perform(post("/api/v1/job-sources/{id}/disable", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/job-sources/{id}/enable", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post("/api/v1/job-sources/{id}/archive", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(post("/api/v1/job-sources/{id}/enable", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStaleSourceUpdate() throws Exception {
        var created = createSource("Example board", "GREENHOUSE", "example-board", "DEFAULT");
        var id = created.get("id").asText();

        mvc.perform(put("/api/v1/job-sources/{id}", id).with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Updated board", "GREENHOUSE", "example-board", "DEFAULT", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordVersion").value(1));
        mvc.perform(put("/api/v1/job-sources/{id}", id).with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Stale board", "GREENHOUSE", "example-board", "DEFAULT", 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void validatesSourceTypeRegionIdentifierBoundsAndActiveIdentity() throws Exception {
        createSource("Canonical Lever", "LEVER", "Example-Site", "DEFAULT");
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Duplicate", "LEVER", "example-site", "GLOBAL", null)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("URL is forbidden", "LEVER", "https://example.test/jobs", "GLOBAL", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Invalid region", "GREENHOUSE", "example-board", "EU", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson("Manual config", "MANUAL", "manual", "DEFAULT", null)))
                .andExpect(status().isBadRequest());

        var outOfBounds = mapper.readTree(sourceJson("Too large", "GREENHOUSE", "other-board", "DEFAULT", null));
        ((com.fasterxml.jackson.databind.node.ObjectNode) outOfBounds).put("pageSize", 101);
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(outOfBounds)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pageSize").exists());
    }

    @Test
    void emailWebhookRequiresCorrectSecretAndRejectsRawBodyField() throws Exception {
        createSource("Java Backend Alert", "EMAIL_WEBHOOK", "Java Backend Alert", "DEFAULT");
        var payload = emailPayload("message-secret-test", validJob("email-job-1"));

        mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Required header missing"));
        mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Webhook authentication failed"));

        var withRawBody = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(payload);
        withRawBody.put("rawEmailBody", "This field must never be accepted or stored");
        mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(withRawBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void ingestsEmailEventAndTreatsReplayAsIdempotent() throws Exception {
        createSource("Java Backend Alert", "EMAIL_WEBHOOK", "Java Backend Alert", "DEFAULT");
        var payload = emailPayload("message-replay-1", validJob("email-job-1"));

        var first = body(mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.jobCount").value(1))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(first.get("eventId").asText()))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.results", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(emailEvents.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD))
                        .param("sourceType", "EMAIL_WEBHOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void emailEventReturnsSafePartialResultForInvalidIndividualJob() throws Exception {
        createSource("Java Backend Alert", "EMAIL_WEBHOOK", "Java Backend Alert", "DEFAULT");
        var valid = validJob("email-job-valid");
        var invalid = mapper.createObjectNode()
                .put("externalId", "email-job-invalid")
                .put("title", "Missing company")
                .put("description", "This individual item is intentionally invalid");
        var payload = emailPayload("message-partial-1", valid, invalid);

        mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL_SUCCESS"))
                .andExpect(jsonPath("$.jobCount").value(2))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.results[1].errorCode").value("INVALID_JOB"))
                .andExpect(jsonPath("$.results[1].errorMessage")
                        .value("The mapped job could not be normalized or stored"));
    }

    @Test
    void preventsOverlappingRunsAndExposesQueuedRun() throws Exception {
        var source = createSource("Queued Lever", "LEVER", "queued-lever", "GLOBAL");
        var sourceId = source.get("id").asText();
        var queued = body(mvc.perform(post("/api/v1/job-sources/{sourceId}/sync", sourceId)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/job-sources/{sourceId}/sync", sourceId)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/job-source-runs/{runId}", queued.get("runId").asText())
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(sourceId))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.coverage").value("COMPLETE_INVENTORY"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(get("/api/v1/job-sources/{sourceId}/runs", sourceId)
                        .with(httpBasic(USER, PASSWORD)).param("status", "QUEUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reconcilesStaleQueuedRun() {
        var source = sourceService.create(sourceRequest(
                "Stale Lever", JobSourceType.LEVER, "stale-lever", SourceRegion.GLOBAL, null));
        var stale = runRepository.saveAndFlush(new JobSourceRun(
                source.id(), JobSourceTriggerType.MANUAL, Instant.now().minusSeconds(180)));

        runPersistence.reconcileStale();

        var recovered = runPersistence.get(stale.id());
        org.assertj.core.api.Assertions.assertThat(recovered.status()).isEqualTo(JobSourceRunStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(recovered.safeErrorCode()).isEqualTo("STALE_RUN_RECOVERED");
        org.assertj.core.api.Assertions.assertThat(recovered.completedAt()).isNotNull();
    }

    @Test
    void persistsRunMetricsCheckpointAndSafeRecordErrors() {
        var source = sourceService.create(sourceRequest(
                "Metrics board", JobSourceType.GREENHOUSE, "metrics-board", SourceRegion.DEFAULT, null));
        var queued = runPersistence.queue(source.id(), JobSourceTriggerType.RETRY);
        runPersistence.begin(queued.id());
        runPersistence.complete(
                queued.id(),
                JobSourceRunStatus.PARTIAL_SUCCESS,
                "complete=false",
                new RunMetrics(5, 2, 1, 1, 0, 1, 0),
                List.of(new RunProblem("bad-record", "INVALID_RECORD", "A mapped record was invalid")));

        var completed = runPersistence.get(queued.id());
        org.assertj.core.api.Assertions.assertThat(completed.status()).isEqualTo(JobSourceRunStatus.PARTIAL_SUCCESS);
        org.assertj.core.api.Assertions.assertThat(completed.checkpoint()).isEqualTo("complete=false");
        org.assertj.core.api.Assertions.assertThat(completed.discoveredCount()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(completed.createdCount()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(completed.updatedCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(completed.unchangedCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(completed.failedCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(completed.errors()).singleElement().satisfies(error -> {
            org.assertj.core.api.Assertions.assertThat(error.externalId()).isEqualTo("bad-record");
            org.assertj.core.api.Assertions.assertThat(error.safeErrorCode()).isEqualTo("INVALID_RECORD");
            org.assertj.core.api.Assertions.assertThat(error.safeErrorMessage()).isEqualTo("A mapped record was invalid");
        });
        var updatedSource = sourceService.get(source.id());
        org.assertj.core.api.Assertions.assertThat(updatedSource.lastAttemptedSyncAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(updatedSource.lastSuccessfulSyncAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(updatedSource.consecutiveFailureCount()).isEqualTo(1);
    }

    private JsonNode createSource(String displayName, String type, String identifier, String region) throws Exception {
        return body(mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceJson(displayName, type, identifier, region, null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String sourceJson(
            String displayName, String type, String identifier, String region, Long recordVersion) throws Exception {
        var node = mapper.createObjectNode()
                .put("displayName", displayName)
                .put("sourceType", type)
                .put("providerIdentifier", identifier)
                .put("region", region)
                .put("enabled", true)
                .put("pageSize", 25)
                .put("maximumPagesPerRun", 4)
                .put("missingRunThreshold", 2);
        if (recordVersion == null) node.putNull("recordVersion");
        else node.put("recordVersion", recordVersion);
        return mapper.writeValueAsString(node);
    }

    private static JobSourceConfigurationRequest sourceRequest(
            String displayName,
            JobSourceType type,
            String identifier,
            SourceRegion region,
            Long version) {
        return new JobSourceConfigurationRequest(displayName, type, identifier, region, true, 25, 4, 2, version);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode validJob(String externalId) {
        return mapper.createObjectNode()
                .put("externalId", externalId)
                .put("company", "Example Company")
                .put("title", "Backend Engineer")
                .put("location", "Example City")
                .put("description", "Build and maintain fictional backend services.")
                .put("applyUrl", "https://careers.example/jobs/" + externalId);
    }

    private String emailPayload(String messageId, JsonNode... jobs) throws Exception {
        var root = mapper.createObjectNode()
                .put("messageId", messageId)
                .put("provider", "GMAIL")
                .put("receivedAt", "2026-08-22T10:00:00Z")
                .put("sourceName", "Java Backend Alert");
        var array = root.putArray("jobs");
        for (var job : jobs) array.add(job);
        return mapper.writeValueAsString(root);
    }

    private JsonNode body(String json) throws Exception {
        return mapper.readTree(json);
    }
}
