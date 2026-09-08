package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.services.blocking.ResponseService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenAiApplicationContentGeneratorTest {
    private static final UUID EXECUTION_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID FACT_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID REQUIREMENT_ID = UUID.fromString("92000000-0000-0000-0000-000000000001");

    @Test
    void sendsTheExactVersionedPlanSchemaAndTracksValidatedRawOutput() throws Exception {
        String rawOutput = """
                {"selectedFactIds":["91000000-0000-0000-0000-000000000001"],
                 "selectedRequirementIds":["92000000-0000-0000-0000-000000000001"],
                 "orderedSkills":["Java"],"unsupportedRequirements":[],"omissions":[],
                 "tone":"concise-professional"}
                """;
        Fixture fixture = fixture(rawOutput);

        TailoringPlan plan = fixture.generator.plan(request());

        assertThat(plan.selectedFactIds()).containsExactly(FACT_ID);
        assertThat(plan.selectedRequirementIds()).containsExactly(REQUIREMENT_ID);
        ResponseCreateParams params = capturedParams(fixture.responses);
        ResponseFormatTextJsonSchemaConfig format = params.text()
                .orElseThrow()
                .format()
                .orElseThrow()
                .asJsonSchema();
        assertThat(format.name()).isEqualTo("application_plan");
        assertThat(format.strict()).contains(true);
        assertThat(format._type().convert(String.class)).isEqualTo("json_schema");
        assertThat(fixture.catalog.schemaChecksum).isEqualTo(ApplicationGenerationPromptCatalog.hash(
                fixture.catalog.planSchema + "\n" + fixture.catalog.writeSchema));
        assertThat(fixture.catalog.planSchemaDocument)
                .isEqualTo(fixture.mapper.readTree(fixture.catalog.planSchema));
        assertThat(schemaNode(fixture.mapper, format.schema()))
                .isEqualTo(fixture.catalog.planSchemaDocument);
        verify(fixture.tracker).succeeded(
                eq(EXECUTION_ID), eq("resp-test"), eq(rawOutput), anyLong(), isNull(), isNull(), isNull());
        verify(fixture.tracker, never()).failed(any(), any(), anyLong(), eq(false));
    }

    @Test
    void writingRequestUsesExactSchemaSelectedResumeEvidenceAndFullJobDescription() throws Exception {
        Fixture fixture = fixture("""
                {"contents":[],"claims":[],"warnings":[],"unsupportedRequirements":[]}
                """);
        VerifiedFactSnapshot omittedFact = fact(
                "91000000-0000-0000-0000-000000000002", "Omitted candidate fact");
        JobRequirementSnapshot omittedRequirement = requirement(
                "92000000-0000-0000-0000-000000000002", "Omitted job requirement");
        ApplicationContentGenerationRequest request = new ApplicationContentGenerationRequest(
                UUID.fromString("93000000-0000-0000-0000-000000000001"),
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "Backend Engineer",
                "Example Systems",
                "Raw description containing an omitted requirement",
                List.of(omittedFact, fact(FACT_ID.toString(), "Selected Java fact")),
                List.of(requirement(REQUIREMENT_ID.toString(), "Selected Java requirement"), omittedRequirement),
                List.of("Why this role?"));
        TailoringPlan plan = new TailoringPlan(
                List.of(FACT_ID),
                List.of(REQUIREMENT_ID),
                List.of("Java"),
                List.of(),
                List.of(),
                "concise-professional");

        fixture.generator.generate(request, plan);

        ResponseCreateParams params = capturedParams(fixture.responses);
        ResponseFormatTextJsonSchemaConfig format = params.text()
                .orElseThrow()
                .format()
                .orElseThrow()
                .asJsonSchema();
        assertThat(format.name()).isEqualTo("application_content");
        assertThat(format.strict()).contains(true);
        assertThat(schemaNode(fixture.mapper, format.schema()))
                .isEqualTo(fixture.catalog.writeSchemaDocument);

        JsonNode input = fixture.mapper.readTree(params.input().orElseThrow().asText());
        JsonNode source = input.path("source");
        assertThat(source.path("jobDescription").textValue())
                .isEqualTo("Raw description containing an omitted requirement");
        assertThat(source.path("facts")).hasSize(1);
        assertThat(source.path("facts").get(0).path("id").textValue()).isEqualTo(FACT_ID.toString());
        assertThat(source.path("jobRequirements")).hasSize(1);
        assertThat(source.path("jobRequirements").get(0).path("id").textValue())
                .isEqualTo(REQUIREMENT_ID.toString());
        assertThat(params.input().orElseThrow().asText())
                .doesNotContain("Omitted candidate fact", "Omitted job requirement");
    }

    @Test
    void malformedUuidFailsBeforeProviderExecutionIsMarkedSuccessful() {
        Fixture fixture = fixture("""
                {"selectedFactIds":["not-a-uuid"],"selectedRequirementIds":[],
                 "orderedSkills":[],"unsupportedRequirements":[],"omissions":[],
                 "tone":"concise-professional"}
                """);

        assertThatThrownBy(() -> fixture.generator.plan(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTENT_GENERATION_SCHEMA_VIOLATION");

        verify(fixture.tracker).failed(eq(EXECUTION_ID), any(IllegalStateException.class), anyLong(), eq(false));
        verify(fixture.tracker, never()).succeeded(any(), anyString(), anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void malformedEnumFailsBeforeProviderExecutionIsMarkedSuccessful() {
        Fixture fixture = fixture("""
                {"contents":[{"type":"INVENTED_TYPE","key":"headline","order":0,"text":"Text"}],
                 "claims":[],"warnings":[],"unsupportedRequirements":[]}
                """);
        TailoringPlan plan = new TailoringPlan(
                List.of(FACT_ID),
                List.of(REQUIREMENT_ID),
                List.of("Java"),
                List.of(),
                List.of(),
                "concise-professional");

        assertThatThrownBy(() -> fixture.generator.generate(request(), plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTENT_GENERATION_SCHEMA_VIOLATION");

        verify(fixture.tracker).failed(eq(EXECUTION_ID), any(IllegalStateException.class), anyLong(), eq(false));
        verify(fixture.tracker, never()).succeeded(any(), anyString(), anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void boundedRepairSendsOnlySafeValidationCodesWithSelectedEvidenceAndJobDescription() throws Exception {
        Fixture fixture = fixture("""
                {"contents":[],"claims":[],"warnings":[],"unsupportedRequirements":[]}
                """);
        TailoringPlan plan = new TailoringPlan(
                List.of(FACT_ID), List.of(REQUIREMENT_ID), List.of("Java"),
                List.of(), List.of(), "concise-professional");

        fixture.generator.repair(
                request(), plan, List.of("UNSUPPORTED_NUMERIC_OR_DATE_VALUE"));

        ResponseCreateParams params = capturedParams(fixture.responses);
        JsonNode input = fixture.mapper.readTree(params.input().orElseThrow().asText());
        assertThat(input.path("validationErrors").get(0).textValue())
                .isEqualTo("UNSUPPORTED_NUMERIC_OR_DATE_VALUE");
        assertThat(input.path("source").path("jobDescription").textValue()).isEqualTo("Job description");
        verify(fixture.tracker).started(
                eq("APPLICATION_CONTENT_REPAIR"), eq(params.input().orElseThrow().asText()), any());
    }

    private static Fixture fixture(String rawOutput) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ContentGenerationProperties config = config();
        ApplicationGenerationPromptCatalog catalog = new ApplicationGenerationPromptCatalog(config, mapper);
        ApplicationLlmExecutionTracker tracker = mock(ApplicationLlmExecutionTracker.class);
        ApplicationGenerationMetrics metrics = mock(ApplicationGenerationMetrics.class);
        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responses = mock(ResponseService.class);
        Response response = mock(Response.class);
        ResponseOutputItem outputItem = mock(ResponseOutputItem.class);
        ResponseOutputMessage message = mock(ResponseOutputMessage.class);
        ResponseOutputMessage.Content content = mock(ResponseOutputMessage.Content.class);
        ResponseOutputText outputText = mock(ResponseOutputText.class);

        when(client.responses()).thenReturn(responses);
        when(responses.create(any(ResponseCreateParams.class))).thenReturn(response);
        when(response.output()).thenReturn(List.of(outputItem));
        when(response.incompleteDetails()).thenReturn(Optional.empty());
        when(response.usage()).thenReturn(Optional.empty());
        when(response.id()).thenReturn("resp-test");
        when(outputItem.message()).thenReturn(Optional.of(message));
        when(message.content()).thenReturn(List.of(content));
        when(content.refusal()).thenReturn(Optional.empty());
        when(content.outputText()).thenReturn(Optional.of(outputText));
        when(outputText.text()).thenReturn(rawOutput);
        when(tracker.started(anyString(), anyString(), any())).thenReturn(EXECUTION_ID);

        return new Fixture(
                new OpenAiApplicationContentGenerator(config, mapper, catalog, tracker, metrics, client),
                mapper,
                catalog,
                tracker,
                responses);
    }

    private static ResponseCreateParams capturedParams(ResponseService responses) {
        ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(responses).create(captor.capture());
        return captor.getValue();
    }

    private static JsonNode schemaNode(
            ObjectMapper mapper, ResponseFormatTextJsonSchemaConfig.Schema schema) {
        ObjectNode node = mapper.createObjectNode();
        schema._additionalProperties()
                .forEach((name, value) -> node.set(name, value.convert(JsonNode.class)));
        return node;
    }

    private static ApplicationContentGenerationRequest request() {
        return new ApplicationContentGenerationRequest(
                UUID.fromString("93000000-0000-0000-0000-000000000001"),
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "Backend Engineer",
                "Example Systems",
                "Job description",
                List.of(fact(FACT_ID.toString(), "Selected Java fact")),
                List.of(requirement(REQUIREMENT_ID.toString(), "Selected Java requirement")),
                List.of());
    }

    private static VerifiedFactSnapshot fact(String id, String statement) {
        return new VerifiedFactSnapshot(
                UUID.fromString(id),
                "EMPLOYMENT",
                statement,
                "Fictional Labs",
                "2021-01-01",
                "2024-12-31",
                Set.of("Java"),
                Set.of("Backend"));
    }

    private static JobRequirementSnapshot requirement(String id, String text) {
        return new JobRequirementSnapshot(UUID.fromString(id), text, "REQUIRED", "SKILL", "MATCHED", text);
    }

    private static ContentGenerationProperties config() {
        return new ContentGenerationProperties(
                true,
                "openai",
                false,
                "gpt-5.6-terra",
                "low",
                Duration.ofSeconds(5),
                0,
                20,
                24_000,
                8_000,
                60_000,
                "v1",
                "v1",
                "ats-single-column-v1");
    }

    private record Fixture(
            OpenAiApplicationContentGenerator generator,
            ObjectMapper mapper,
            ApplicationGenerationPromptCatalog catalog,
            ApplicationLlmExecutionTracker tracker,
            ResponseService responses) {}
}
