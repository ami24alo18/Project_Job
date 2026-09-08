package com.amit.jobagent.application;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaApplicationContentGeneratorTest {
    private static final UUID FACT_ID = UUID.fromString("93000000-0000-0000-0000-000000000011");
    private static final UUID REQUIREMENT_ID = UUID.fromString("93000000-0000-0000-0000-000000000012");
    private static final UUID EXECUTION_ID = UUID.fromString("93000000-0000-0000-0000-000000000013");

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void plansLocallyWithoutSpendingACpuBoundModelCall() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var tracker = mock(ApplicationLlmExecutionTracker.class);
        var generator = generator(mapper, tracker);

        TailoringPlan plan = generator.plan(request());

        assertThat(plan.selectedFactIds()).containsExactly(FACT_ID);
        assertThat(plan.selectedRequirementIds()).containsExactly(REQUIREMENT_ID);
        assertThat(plan.orderedSkills()).containsExactly("Java");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void plansOpenRouterLocallyWithoutConsumingAHostedModelRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var tracker = mock(ApplicationLlmExecutionTracker.class);
        var generator = generator(mapper, tracker, "openrouter");

        TailoringPlan plan = generator.plan(request());

        assertThat(plan.selectedFactIds()).containsExactly(FACT_ID);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void explicitlyDisablesDefaultOpenRouterReasoningWhenConfiguredAsNone() {
        assertThat(OllamaApplicationContentGenerator.openRouterReasoning("none"))
                .containsEntry("effort", "none")
                .containsEntry("exclude", true);
    }

    @Test
    void selectedWritingSourceIncludesTheJobDescription() {
        var plan = new TailoringPlan(
                List.of(FACT_ID), List.of(REQUIREMENT_ID), List.of("Java"),
                List.of(), List.of(), "concise-professional");

        var selected = OpenAiApplicationContentGenerator.selectedSource(request(), plan);

        assertThat(selected.jobDescription()).isEqualTo("Requires Java.");
        assertThat(selected.facts()).extracting(VerifiedFactSnapshot::statement)
                .containsExactly("Built Java services.");
    }

    @Test
    void returnsTheModelDraftWithoutReplacingItWithDeterministicContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String content = mapper.writeValueAsString(new GeneratedApplicationContent(
                List.of(new GeneratedContentItem(
                        GeneratedContentType.PROFESSIONAL_SUMMARY,
                        "summary",
                        0,
                        "Model-authored tailored summary")),
                List.of(new GeneratedClaimAtom(
                        "summary",
                        "Model-authored tailored summary",
                        ClaimType.CANDIDATE_FACT,
                        List.of(FACT_ID),
                        List.of(),
                        null)),
                List.of(),
                List.of()));
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(mapper.writeValueAsString(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "done", true,
                "done_reason", "stop")))));
        var tracker = mock(ApplicationLlmExecutionTracker.class);
        when(tracker.started(anyString(), anyString(), any())).thenReturn(EXECUTION_ID);
        var generator = generator(mapper, tracker);
        var plan = new TailoringPlan(
                List.of(FACT_ID), List.of(REQUIREMENT_ID), List.of("Java"),
                List.of(), List.of(), "concise-professional");

        GeneratedApplicationContent generated = generator.generate(request(), plan);

        assertThat(generated.contents()).singleElement()
                .satisfies(item -> assertThat(item.text()).isEqualTo("Model-authored tailored summary"));
        assertThat(generated.warnings()).doesNotContain("LOCAL_MODEL_OUTPUT_REPLACED_WITH_SAFE_DRAFT");
        assertThat(server.getAllServeEvents()).hasSize(1);
        JsonNode modelRequest = mapper.readTree(
                server.getAllServeEvents().getFirst().getRequest().getBodyAsString());
        assertThat(modelRequest.path("model").asText()).isEqualTo("qwen3.5:4b");
        assertThat(modelRequest.path("think").asBoolean()).isFalse();
        assertThat(modelRequest.path("format").isObject()).isTrue();
        String userInput = modelRequest.path("messages").path(1).path("content").asText();
        assertThat(userInput)
                .contains("Requires Java.")
                .contains("Built Java services.")
                .contains(FACT_ID.toString());
    }

    @Test
    void qwenPromptRequiresUnsupportedJobSkillsToRemainGaps() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ContentGenerationProperties config = new ContentGenerationProperties(
                true, "ollama", false, "qwen3.5:4b", "none", Duration.ofSeconds(10), 0,
                20, 3_000, 8_000, 12_000, "v3", "v1", "ats-single-column-v1");

        var catalog = new ApplicationGenerationPromptCatalog(config, mapper);

        assertThat(catalog.writeSystem)
                .contains("If the JD asks for Kafka but the resume proves only SQS")
                .contains("omit Kafka")
                .contains("Never invent or infer")
                .contains("exactly one supplied factId");
    }

    @Test
    void derivesMissingRequirementsFromEvaluationInsteadOfTrustingModelClassification() {
        var base = request();
        var request = new ApplicationContentGenerationRequest(
                base.profileVersionId(), base.profileChecksum(), base.jobChecksum(),
                base.evaluationChecksum(), base.jobTitle(), base.company(), base.jobDescription(),
                base.facts(),
                List.of(
                        new JobRequirementSnapshot(
                                REQUIREMENT_ID, "Java", "REQUIRED", "SKILL", "MATCHED", "Requires Java"),
                        new JobRequirementSnapshot(
                                UUID.randomUUID(), "Kafka", "REQUIRED", "SKILL", "MISSING", "Requires Kafka"),
                        new JobRequirementSnapshot(
                                UUID.randomUUID(), "Distributed systems", "PREFERRED", "SKILL", "PARTIAL",
                                "Distributed systems preferred")),
                base.questions());

        assertThat(OllamaApplicationContentGenerator.missingRequirements(request))
                .containsExactly("Kafka");
    }

    @Test
    void convertsCompactQwenOutputIntoProvenanceAwareApplicationContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String content = mapper.writeValueAsString(Map.of(
                "summary", Map.of("text", "Built Java services.", "factIds", List.of(FACT_ID)),
                "skills", List.of("Java"),
                "bullets", List.of(Map.of(
                        "kind", "EXPERIENCE", "factId", FACT_ID, "text", "Built Java services.")),
                "warnings", List.of()));
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(mapper.writeValueAsString(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "done", true,
                "done_reason", "stop")))));
        var tracker = mock(ApplicationLlmExecutionTracker.class);
        when(tracker.started(anyString(), anyString(), any())).thenReturn(EXECUTION_ID);
        var generator = generator(mapper, tracker, "ollama", "v2");

        GeneratedApplicationContent generated = generator.generate(request(), generator.plan(request()));

        assertThat(generated.contents()).extracting(GeneratedContentItem::type).containsExactly(
                GeneratedContentType.PROFESSIONAL_SUMMARY,
                GeneratedContentType.SKILL_SECTION,
                GeneratedContentType.EXPERIENCE_BULLET,
                GeneratedContentType.COVER_LETTER,
                GeneratedContentType.RECRUITER_MESSAGE);
        assertThat(generated.claims()).anySatisfy(claim -> {
            assertThat(claim.contentKey()).isEqualTo("bullet-0");
            assertThat(claim.factIds()).containsExactly(FACT_ID);
        });
        assertThat(new GeneratedContentValidator().validate(
                generated,
                request().facts(),
                new JobClaimEvidence(
                        Map.of(REQUIREMENT_ID, "Requires Java"),
                        Map.of("job.title", "Backend Engineer", "job.company", "Example Systems"))))
                .satisfies(result -> assertThat(result.valid()).as(result.codes().toString()).isTrue());
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void filtersUnsupportedModelSkillsAndBindsSupportedSkillsToResumeEvidence() {
        Map<String, VerifiedFactSnapshot> skills = OllamaApplicationContentGenerator.groundedSkills(
                List.of("Kafka", "Java", "java"), request().facts());

        assertThat(skills).containsOnlyKeys("Java");
        assertThat(skills.get("Java").id()).isEqualTo(FACT_ID);
    }

    @Test
    void rejectsBaseUrlWithPath() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OllamaContentGenerationProperties(
                        URI.create("http://localhost:11434/untrusted"), Duration.ofSeconds(5), 0, 4_096, "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OLLAMA_BASE_URL");
    }

    @Test
    void pinsOpenRouterCredentialsToTheOfficialHttpsApi() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OpenRouterContentGenerationProperties(
                URI.create("https://example.test/api/v1"), Duration.ofSeconds(5), 0,
                        "http://localhost:3000", "Test", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENROUTER_BASE_URL");
        var properties = new OpenRouterContentGenerationProperties(
                URI.create("https://openrouter.ai/api/v1"), Duration.ofSeconds(5), 1,
                "http://localhost:3000", "Test", false);
        assertThat(properties.chatEndpoint().toString())
                .isEqualTo("https://openrouter.ai/api/v1/chat/completions");
    }

    @Test
    void pinsHuggingFaceCredentialsToTheOfficialHttpsRouter() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new HuggingFaceContentGenerationProperties(
                        URI.create("https://example.test/v1"), Duration.ofSeconds(5), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HUGGINGFACE_BASE_URL");
        var properties = new HuggingFaceContentGenerationProperties(
                URI.create("https://router.huggingface.co/v1"), Duration.ofSeconds(5), 1);
        assertThat(properties.chatEndpoint().toString())
                .isEqualTo("https://router.huggingface.co/v1/chat/completions");
    }

    @Test
    void groundsRepairFromExactSuppliedEvidence() {
        var generated = new GeneratedApplicationContent(
                List.of(new GeneratedContentItem(
                        GeneratedContentType.PROFESSIONAL_SUMMARY,
                        "summary",
                        7,
                        "Invented polished prose")),
                List.of(
                        new GeneratedClaimAtom(
                                "summary", "Invented polished prose", ClaimType.CANDIDATE_FACT,
                                List.of(FACT_ID), List.of(), null),
                        new GeneratedClaimAtom(
                                "summary", "Backend Engineer", ClaimType.NON_FACTUAL,
                                List.of(), List.of(), "job.title")),
                List.of(),
                List.of());
        var plan = new TailoringPlan(
                List.of(FACT_ID), List.of(REQUIREMENT_ID), List.of("Java"),
                List.of(), List.of(), "concise-professional");

        GeneratedApplicationContent grounded =
                OllamaApplicationContentGenerator.groundRepair(generated, request(), plan);

        assertThat(grounded.contents()).extracting(GeneratedContentItem::type).containsExactly(
                GeneratedContentType.PROFESSIONAL_SUMMARY,
                GeneratedContentType.SKILL_SECTION,
                GeneratedContentType.EXPERIENCE_BULLET,
                GeneratedContentType.COVER_LETTER,
                GeneratedContentType.RECRUITER_MESSAGE);
        assertThat(grounded.contents()).allSatisfy(content -> assertThat(content.text()).isNotBlank());
        assertThat(grounded.contents().stream()
                .filter(content -> content.type() == GeneratedContentType.COVER_LETTER)
                .findFirst().orElseThrow().text())
                .isEqualTo("I am excited to apply for the Backend Engineer role at Example Systems.\n\n"
                        + "Relevant experience: Built Java services.\n\n"
                        + "I would welcome the opportunity to discuss this role.");
        assertThat(new GeneratedContentValidator().validate(
                grounded,
                request().facts(),
                new JobClaimEvidence(
                        Map.of(REQUIREMENT_ID, "Requires Java"),
                        Map.of("job.title", "Backend Engineer", "job.company", "Example Systems"))))
                .satisfies(result -> assertThat(result.valid()).as(result.codes().toString()).isTrue());
        assertThat(grounded.warnings()).contains(
                "MODEL_GROUNDED_FALLBACK",
                "MODEL_OUTPUT_REPLACED_WITH_EVIDENCE_GROUNDED_DRAFT");
        assertThat(OllamaApplicationContentGenerator.meetsMinimumDraftQuality(grounded)).isTrue();
        assertThat(OllamaApplicationContentGenerator.meetsMinimumDraftQuality(generated)).isFalse();
    }

    private OllamaApplicationContentGenerator generator(
            ObjectMapper mapper, ApplicationLlmExecutionTracker tracker) {
        return generator(mapper, tracker, "ollama");
    }

    private OllamaApplicationContentGenerator generator(
            ObjectMapper mapper, ApplicationLlmExecutionTracker tracker, String provider) {
        return generator(mapper, tracker, provider, "v1");
    }

    private OllamaApplicationContentGenerator generator(
            ObjectMapper mapper, ApplicationLlmExecutionTracker tracker, String provider, String schemaVersion) {
        ContentGenerationProperties config = new ContentGenerationProperties(
                true, provider, false, provider.equals("openrouter") ? "z-ai/glm-5.2:free" : "qwen3.5:4b",
                "none", Duration.ofSeconds(10), 0,
                20, 3_000, 3_000, 12_000, "v2".equals(schemaVersion) ? "v3" : "v1",
                schemaVersion, "ats-single-column-v1");
        var ollama = new OllamaContentGenerationProperties(
                URI.create(server.baseUrl()), Duration.ofSeconds(10), 0, 4_096, "0");
        var openRouter = new OpenRouterContentGenerationProperties(
                URI.create("https://openrouter.ai/api/v1"), Duration.ofSeconds(10), 0,
                "http://localhost:3000", "Job Application Agent Test", false);
        var huggingFace = new HuggingFaceContentGenerationProperties(
                URI.create("https://router.huggingface.co/v1"), Duration.ofSeconds(10), 0);
        return new OllamaApplicationContentGenerator(
                config, ollama, openRouter, huggingFace, mapper,
                new ApplicationGenerationPromptCatalog(config, mapper), tracker,
                mock(ApplicationGenerationMetrics.class), HttpClient.newHttpClient());
    }

    private static ApplicationContentGenerationRequest request() {
        return new ApplicationContentGenerationRequest(
                UUID.fromString("93000000-0000-0000-0000-000000000001"),
                "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "Backend Engineer", "Example Systems", "Requires Java.",
                List.of(new VerifiedFactSnapshot(
                        FACT_ID, "EMPLOYMENT", "Built Java services.", "Example Systems",
                        "2021-01-01", "2024-12-31", Set.of("Java"), Set.of("Backend"))),
                List.of(new JobRequirementSnapshot(
                        REQUIREMENT_ID, "Java", "REQUIRED", "SKILL", "MATCHED", "Requires Java")),
                List.of());
    }
}
