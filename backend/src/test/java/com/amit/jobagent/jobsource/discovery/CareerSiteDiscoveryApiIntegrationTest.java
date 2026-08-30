package com.amit.jobagent.jobsource.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.JobSourceSupportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "job-agent.extraction-recipes.enabled=true",
        "job-agent.extraction-recipes.definitions[0].id=example-static",
        "job-agent.extraction-recipes.definitions[0].version=example-static-v1",
        "job-agent.extraction-recipes.definitions[0].canonical-host=careers.recipe-example.com",
        "job-agent.extraction-recipes.definitions[0].allowed-path-prefixes[0]=/jobs",
        "job-agent.extraction-recipes.definitions[0].enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerSiteDiscoveryApiIntegrationTest {
    private static final String USER = "test-user";
    private static final String PASSWORD = "test-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean CareerSiteDiscoveryService service;
    @MockBean CareerSitePageFetcher pages;

    @Test
    void discoveryRequiresBasicAuthentication() throws Exception {
        mvc.perform(post("/api/v1/job-sources/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("American Express", "https://careers.americanexpress.com/en/sites/CX_1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyBoundedDetectionResult() throws Exception {
        when(service.discover(any(), eq(USER))).thenReturn(new CareerSiteDiscoveryResponse(
                "https://careers.americanexpress.com/en/sites/CX_1",
                "careers.americanexpress.com",
                JobSourceConnectorType.ORACLE_CX,
                "CX_1",
                JobSourceSupportStatus.NEEDS_AUTHORIZATION,
                "The detected Oracle integration requires an approved public interface before activation",
                "career-site-detection-v1"));

        mvc.perform(post("/api/v1/job-sources/discover").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("American Express", "https://careers.americanexpress.com/en/sites/CX_1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalHost").value("careers.americanexpress.com"))
                .andExpect(jsonPath("$.connectorType").value("ORACLE_CX"))
                .andExpect(jsonPath("$.providerIdentifier").value("CX_1"))
                .andExpect(jsonPath("$.supportStatus").value("NEEDS_AUTHORIZATION"))
                .andExpect(jsonPath("$.detectionVersion").value("career-site-detection-v1"))
                .andExpect(jsonPath("$.resolvedAddresses").doesNotExist())
                .andExpect(jsonPath("$.responseBody").doesNotExist());
    }

    @Test
    void rejectsUnknownOrMissingRequestFields() throws Exception {
        mvc.perform(post("/api/v1/job-sources/discover").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Example\",\"careerSiteUrl\":\"https://careers.example.com\",\"headers\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        mvc.perform(post("/api/v1/job-sources/discover").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"\",\"careerSiteUrl\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.careerSiteUrl").exists());
    }

    @Test
    void mapsRemoteInspectionFailuresToSafeProblemDetails() throws Exception {
        when(service.discover(any(), eq(USER))).thenThrow(new CareerSiteDiscoveryException(
                HttpStatus.UNPROCESSABLE_ENTITY, "REDIRECT_LIMIT_EXCEEDED",
                "The career site exceeded the redirect limit"));

        mvc.perform(post("/api/v1/job-sources/discover").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Example", "https://careers.example.com/jobs")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Career site inspection failed"))
                .andExpect(jsonPath("$.errorCode").value("REDIRECT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("The career site exceeded the redirect limit"));
    }

    @Test
    void createsOnlyFromServerDetectionAndConnectionTestsTheReviewedAdapter() throws Exception {
        String url = "https://careers.example.com/jobs/phase8-api-123";
        when(service.discover(any(), eq(USER))).thenReturn(new CareerSiteDiscoveryResponse(
                url, "careers.example.com", JobSourceConnectorType.GENERIC_JSON_LD,
                "careers.example.com-phase8", JobSourceSupportStatus.SUPPORTED,
                "This individual job page can be imported through its JobPosting structured data",
                "career-site-detection-v1"));
        when(pages.fetch(url)).thenReturn(new CareerSitePage(url, "text/html", """
                <script type="application/ld+json">{"@type":"JobPosting","identifier":"API-123",
                "title":"Backend Engineer","hiringOrganization":{"name":"Example Co"},"url":"%s"}</script>
                """.formatted(url)));

        String createdBody = mvc.perform(post("/api/v1/job-sources/career-site").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Example Co","careerSiteUrl":"%s","enabled":true,
                                 "pageSize":10,"maximumPagesPerRun":1,"missingRunThreshold":2}
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("CAREER_SITE"))
                .andExpect(jsonPath("$.connectorType").value("GENERIC_JSON_LD"))
                .andExpect(jsonPath("$.supportStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(createdBody).path("id").asText();

        mvc.perform(post("/api/v1/job-sources/{id}/test", id).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.discoveredCount").value(1))
                .andExpect(jsonPath("$.source.lastConnectionTestStatus").value("SUCCEEDED"));
    }

    @Test
    void detectedAuthorizationRequirementCannotBeEnabledByTheClient() throws Exception {
        String url = "https://oracle.example.com/en/sites/CX_PHASE8";
        when(service.discover(any(), eq(USER))).thenReturn(new CareerSiteDiscoveryResponse(
                url, "oracle.example.com", JobSourceConnectorType.ORACLE_CX, "CX_PHASE8",
                JobSourceSupportStatus.NEEDS_AUTHORIZATION,
                "The detected Oracle integration requires an approved public interface before activation",
                "career-site-detection-v1"));

        mvc.perform(post("/api/v1/job-sources/career-site").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Oracle Example","careerSiteUrl":"%s","enabled":true,
                                 "pageSize":10,"maximumPagesPerRun":2,"missingRunThreshold":2}
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supportStatus").value("NEEDS_AUTHORIZATION"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void reviewedRecipeAssociationIssuesOneTimeTokenAndPreservesRecipeProvenance() throws Exception {
        String url = "https://careers.recipe-example.com/jobs";
        when(service.discover(any(), eq(USER))).thenReturn(new CareerSiteDiscoveryResponse(
                url, "careers.recipe-example.com", JobSourceConnectorType.CUSTOM_RECIPE,
                "careers.recipe-example.com", JobSourceSupportStatus.NEEDS_EXTRACTION_RECIPE,
                "A reviewed isolated extraction recipe is required", "career-site-detection-v1"));

        String created = mvc.perform(post("/api/v1/job-sources/career-site").with(httpBasic(USER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Recipe Example","careerSiteUrl":"%s","enabled":true,
                                 "pageSize":10,"maximumPagesPerRun":1,"missingRunThreshold":2}
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(false))
                .andReturn().getResponse().getContentAsString();
        String sourceId = mapper.readTree(created).path("id").asText();

        String associated = mvc.perform(post("/api/v1/job-sources/{id}/recipe", sourceId)
                        .with(httpBasic(USER, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":\"example-static\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.supportStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.source.extractionRecipeVersion").value("example-static-v1"))
                .andExpect(jsonPath("$.webhookToken").isString())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(associated).path("webhookToken").asText();

        String event = mvc.perform(post("/api/v1/job-sources/{id}/external-events", sourceId)
                        .header("X-Job-Agent-Webhook-Token", token)
                        .header("Idempotency-Key", "recipe-run-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"recipe-run-001","ingestionProvider":"CUSTOM_RECIPE",
                                 "fetchedAt":"2026-08-29T10:00:00Z","jobs":[{
                                   "externalId":"recipe-job-001","originPublisher":"RECIPE_EXAMPLE",
                                   "company":"Recipe Example","title":"Senior Backend Engineer",
                                   "description":"Reviewed worker output","sourceUrl":"https://careers.recipe-example.com/jobs/1",
                                   "applyUrl":"https://careers.recipe-example.com/jobs/1/apply"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn().getResponse().getContentAsString();
        String jobId = mapper.readTree(event).path("results").get(0).path("jobId").asText();

        mvc.perform(get("/api/v1/jobs/{id}", jobId).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingestionProvider").value("CUSTOM_RECIPE"))
                .andExpect(jsonPath("$.extractionRecipeVersion").value("example-static-v1"));
    }

    private static String request(String company, String url) {
        return "{\"companyName\":\"" + company + "\",\"careerSiteUrl\":\"" + url + "\"}";
    }
}
