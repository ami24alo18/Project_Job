package com.amit.jobagent;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class Phase3JobApiIntegrationTest {
    private static final String USER = "test-user";
    private static final String PASSWORD = "test-password";
    private static final String WEBHOOK_SECRET = "test-n8n-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void jobEndpointsRequireAuthentication() throws Exception {
        String id = UUID.fromString("10000000-0000-0000-0000-000000000001").toString();

        mvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/jobs/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/jobs/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/jobs/manual").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(manual("Example", "Engineer", "Description", null))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/jobs/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/jobs/{id}/archive", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/jobs/{id}/duplicates", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsNormalizedManualJobAndReplaysIdempotencyKey() throws Exception {
        var request = manual(
                "  Example   Systems  ",
                "  Backend   Engineer  ",
                "Build reliable fictional services.",
                "HTTPS://careers.example.test:443/roles/123/?utm_source=alert&job=123#apply");
        request.put("location", "  Noida   NCR ");
        request.put("countryCode", "in");
        request.put("workplaceType", "HYBRID");
        request.put("employmentType", "FULL_TIME");
        request.put("salaryMinimum", 100000);
        request.put("salaryMaximum", 150000);
        request.put("salaryCurrency", "inr");
        request.put("salaryInterval", "YEAR");

        var first = body(mvc.perform(post("/api/v1/jobs/manual")
                        .with(httpBasic(USER, PASSWORD))
                        .header("Idempotency-Key", "manual:mockmvc:1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.company").value("Example Systems"))
                .andExpect(jsonPath("$.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.location").value("Noida NCR"))
                .andExpect(jsonPath("$.countryCode").value("IN"))
                .andExpect(jsonPath("$.salaryCurrency").value("INR"))
                .andExpect(jsonPath("$.applyUrl").value("https://careers.example.test/roles/123?job=123"))
                .andExpect(jsonPath("$.status").value("READY_FOR_EVALUATION"))
                .andExpect(jsonPath("$.ingestionProvider").value("MANUAL"))
                .andExpect(jsonPath("$.recordVersion").value(0))
                .andReturn().getResponse().getContentAsString());

        var changedReplay = manual(
                "Different Replay Company", "Different replay title", "A changed retry body",
                "https://other.example.test/jobs/different");
        mvc.perform(post("/api/v1/jobs/manual")
                        .with(httpBasic(USER, PASSWORD))
                        .header("Idempotency-Key", "manual:mockmvc:1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(changedReplay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.get("id").asText()))
                .andExpect(jsonPath("$.company").value("Example Systems"))
                .andExpect(jsonPath("$.title").value("Backend Engineer"));
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsInvalidManualJobRequests() throws Exception {
        var missingIdentity = manual(" ", "", "Description remains present", null);
        mvc.perform(post("/api/v1/jobs/manual").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(missingIdentity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.company").exists())
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        var missingContent = manual("Example Systems", "Engineer", null, null);
        mvc.perform(post("/api/v1/jobs/manual").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(missingContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("At least one of description or apply URL is required"));

        var invalidUrl = manual("Example Systems", "Engineer", null, "file:///etc/passwd");
        mvc.perform(post("/api/v1/jobs/manual").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalidUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("URL must be a valid HTTP or HTTPS URL"));
    }

    @Test
    void convertsUntrustedManualDescriptionHtmlToBoundedPlainText() throws Exception {
        var request = manual(
                "Example Systems",
                "HTML Safety Engineer",
                "<h2>Fictional role</h2><script>alert('x')</script><style>body{display:none}</style><p>Build systems</p>",
                null);

        mvc.perform(post("/api/v1/jobs/manual").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.descriptionPlainText").value("Fictional role Build systems"));
    }

    @Test
    void listsWithFiltersExcludesDuplicatesAndArchivesByDefaultAndCapsPageSize() throws Exception {
        createManual("list:original", manual(
                "Example Systems", "Backend Engineer", "Original role", "https://careers.example.test/jobs/shared"));
        createManual("list:duplicate", manual(
                "Another Display Company", "Other feed title", "Duplicate role", "https://careers.example.test/jobs/shared"));
        var archived = createManual("list:archived", manual(
                "Archived Company", "Archived Engineer", "Archived role", "https://careers.example.test/jobs/archived"));
        mvc.perform(post("/api/v1/jobs/{id}/archive", archived.get("id").asText())
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk());
        createManual("list:active", manual(
                "Other Company", "Platform Engineer", "Another active role", "https://careers.example.test/jobs/active"));

        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD))
                        .param("sourceType", "MANUAL")
                        .param("company", "example")
                        .param("title", "backend")
                        .param("location", "noida")
                        .param("workplaceType", "REMOTE")
                        .param("employmentType", "FULL_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Backend Engineer"));
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD))
                        .param("includeDuplicates", "true")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/jobs").with(httpBasic(USER, PASSWORD)).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void summaryReturnsCountsFromPersistedLifecycleStates() throws Exception {
        var expiring = createManual("summary:expiring", manual(
                "Example Systems", "Engineer", "Ready role", "https://careers.example.test/jobs/summary-shared"));
        createManual("summary:duplicate", manual(
                "Other Company", "Other title", "Duplicate role", "https://careers.example.test/jobs/summary-shared"));
        var archived = createManual("summary:archived", manual(
                "Archived Company", "Archived role", "Archived description", "https://careers.example.test/jobs/summary-archived"));
        mvc.perform(post("/api/v1/jobs/{id}/archive", archived.get("id").asText())
                        .with(httpBasic(USER, PASSWORD))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/jobs/{id}/mark-expired", expiring.get("id").asText())
                        .with(httpBasic(USER, PASSWORD))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/jobs/summary").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.readyForEvaluation").value(0))
                .andExpect(jsonPath("$.needsReview").value(0))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.expired").value(1))
                .andExpect(jsonPath("$.sourceRemoved").value(0))
                .andExpect(jsonPath("$.archived").value(1));
    }

    @Test
    void editsManualAndProviderJobsAndRejectsStaleRecordVersion() throws Exception {
        var manual = createManual("edit:manual", manual(
                "Example Systems", "Backend Engineer", "Original manual description",
                "https://careers.example.test/jobs/edit-manual"));
        var manualUpdate = update(
                "Example Systems", "Senior Backend Engineer", "Edited manual description",
                manual.get("recordVersion").asLong());
        mvc.perform(put("/api/v1/jobs/{id}", manual.get("id").asText())
                        .with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(manualUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.manuallyEdited").value(false))
                .andExpect(jsonPath("$.recordVersion").value(1));

        String providerId = createEmailProviderJob();
        var provider = body(mvc.perform(get("/api/v1/jobs/{id}", providerId)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var providerUpdate = update(
                "Provider Company", "Corrected Provider Engineer", "Human-reviewed provider correction",
                provider.get("recordVersion").asLong());
        mvc.perform(put("/api/v1/jobs/{id}", providerId)
                        .with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(providerUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("EMAIL_WEBHOOK"))
                .andExpect(jsonPath("$.title").value("Corrected Provider Engineer"))
                .andExpect(jsonPath("$.manuallyEdited").value(true))
                .andExpect(jsonPath("$.sourceUpdateAvailable").value(false));

        var stale = update("Example Systems", "Stale title", "Stale edit", 0);
        mvc.perform(put("/api/v1/jobs/{id}", manual.get("id").asText())
                        .with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void archivesRestoresAndMarksJobExpired() throws Exception {
        var created = createManual("lifecycle:1", manual(
                "Example Systems", "Lifecycle Engineer", "Lifecycle role",
                "https://careers.example.test/jobs/lifecycle"));
        String id = created.get("id").asText();

        mvc.perform(post("/api/v1/jobs/{id}/archive", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(post("/api/v1/jobs/{id}/restore", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_EVALUATION"));
        mvc.perform(post("/api/v1/jobs/{id}/mark-expired", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    private ObjectNode manual(String company, String title, String description, String applyUrl) {
        var node = mapper.createObjectNode()
                .put("company", company)
                .put("title", title)
                .put("location", "Noida")
                .put("countryCode", "IN")
                .put("workplaceType", "REMOTE")
                .put("employmentType", "FULL_TIME")
                .put("department", "Engineering")
                .put("team", "Platform");
        if (description != null) node.put("description", description);
        if (applyUrl != null) node.put("applyUrl", applyUrl);
        return node;
    }

    private ObjectNode update(String company, String title, String description, long recordVersion) {
        return mapper.createObjectNode()
                .put("company", company)
                .put("title", title)
                .put("location", "Noida")
                .put("countryCode", "IN")
                .put("workplaceType", "REMOTE")
                .put("employmentType", "FULL_TIME")
                .put("department", "Engineering")
                .put("team", "Platform")
                .put("description", description)
                .put("recordVersion", recordVersion);
    }

    private JsonNode createManual(String idempotencyKey, ObjectNode request) throws Exception {
        return body(mvc.perform(post("/api/v1/jobs/manual")
                        .with(httpBasic(USER, PASSWORD))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String createEmailProviderJob() throws Exception {
        var source = mapper.createObjectNode()
                .put("displayName", "MockMvc email source")
                .put("sourceType", "EMAIL_WEBHOOK")
                .put("providerIdentifier", "MockMvc Alert")
                .put("region", "DEFAULT")
                .put("enabled", true)
                .put("pageSize", 25)
                .put("maximumPagesPerRun", 4)
                .put("missingRunThreshold", 2);
        mvc.perform(post("/api/v1/job-sources").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(source)))
                .andExpect(status().isOk());

        var event = mapper.createObjectNode()
                .put("messageId", "mockmvc-provider-edit-message")
                .put("provider", "GMAIL")
                .put("receivedAt", "2026-08-22T10:00:00Z")
                .put("sourceName", "MockMvc Alert");
        event.putArray("jobs").add(mapper.createObjectNode()
                .put("externalId", "mockmvc-provider-job")
                .put("company", "Provider Company")
                .put("title", "Provider Engineer")
                .put("location", "Noida")
                .put("description", "Provider-controlled description")
                .put("applyUrl", "https://careers.example.test/jobs/provider-edit"));
        var response = body(mvc.perform(post("/api/v1/job-sources/email-alert/events")
                        .header("X-N8N-WEBHOOK-SECRET", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andReturn().getResponse().getContentAsString());
        return response.get("results").get(0).get("jobId").asText();
    }

    private JsonNode body(String json) throws Exception {
        return mapper.readTree(json);
    }
}
