package com.amit.jobagent.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseTextConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${job-agent.content-generation.enabled:false}' == 'true' && '${job-agent.content-generation.provider:openai}' == 'openai'")
class OpenAiApplicationContentGenerator implements ApplicationContentGenerator {
    private final OpenAIClient client;
    private final ContentGenerationProperties config;
    private final ObjectMapper mapper;
    private final ApplicationGenerationPromptCatalog prompts;
    private final ApplicationLlmExecutionTracker tracker;
    private final ApplicationGenerationMetrics metrics;

    @Autowired
    OpenAiApplicationContentGenerator(
            ContentGenerationProperties config,
            ObjectMapper mapper,
            ApplicationGenerationPromptCatalog prompts,
            ApplicationLlmExecutionTracker tracker,
            ApplicationGenerationMetrics metrics) {
        this(config, mapper, prompts, tracker, metrics, createClient(config));
    }

    OpenAiApplicationContentGenerator(
            ContentGenerationProperties config,
            ObjectMapper mapper,
            ApplicationGenerationPromptCatalog prompts,
            ApplicationLlmExecutionTracker tracker,
            ApplicationGenerationMetrics metrics,
            OpenAIClient client) {
        this.config = config;
        this.mapper = mapper;
        this.prompts = prompts;
        this.tracker = tracker;
        this.metrics = metrics;
        this.client = client;
    }

    private static OpenAIClient createClient(ContentGenerationProperties config) {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Content generation is enabled but OPENAI_API_KEY is missing");
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(key)
                .timeout(config.timeout())
                .maxRetries(config.maxRetries())
                .build();
    }

    @Override
    public TailoringPlan plan(ApplicationContentGenerationRequest request) {
        return call(
                "APPLICATION_PLAN",
                prompts.planSystem,
                json(request),
                request,
                "application_plan",
                prompts.planSchemaDocument,
                PlanOutput.class,
                output -> new TailoringPlan(
                        safe(output.selectedFactIds),
                        safe(output.selectedRequirementIds),
                        safe(output.orderedSkills),
                        safe(output.unsupportedRequirements),
                        safe(output.omissions),
                        output.tone == null ? "concise-professional" : output.tone));
    }

    @Override
    public GeneratedApplicationContent generate(ApplicationContentGenerationRequest request, TailoringPlan plan) {
        return write(request, plan, List.of(), "APPLICATION_CONTENT");
    }

    @Override
    public GeneratedApplicationContent repair(
            ApplicationContentGenerationRequest request,
            TailoringPlan plan,
            List<String> validationCodes) {
        return write(request, plan, safe(validationCodes), "APPLICATION_CONTENT_REPAIR");
    }

    private GeneratedApplicationContent write(
            ApplicationContentGenerationRequest request,
            TailoringPlan plan,
            List<String> validationCodes,
            String operation) {
        var input = new LinkedHashMap<String, Object>();
        var selectedSource = selectedSource(request, plan);
        input.put("validatedPlan", plan);
        input.put("source", selectedSource);
        if (!validationCodes.isEmpty()) input.put("validationErrors", validationCodes);
        return call(
                operation,
                prompts.writeSystem,
                json(input),
                selectedSource,
                "application_content",
                prompts.writeSchemaDocument,
                WriteOutput.class,
                output -> {
                    var content = safe(output.contents).stream()
                            .map(value -> new GeneratedContentItem(
                                    value.type, value.key, value.order, value.text))
                            .toList();
                    var claims = safe(output.claims).stream()
                            .map(value -> new GeneratedClaimAtom(
                                    value.contentKey,
                                    value.claimText,
                                    value.claimType,
                                    safe(value.factIds),
                                    safe(value.requirementIds),
                                    value.jobFieldReference))
                            .toList();
                    return new GeneratedApplicationContent(
                            content, claims, safe(output.warnings), safe(output.unsupportedRequirements));
                });
    }

    static ApplicationContentGenerationRequest selectedSource(
            ApplicationContentGenerationRequest request, TailoringPlan plan) {
        Set<UUID> selectedFactIds = Set.copyOf(plan.selectedFactIds());
        Set<UUID> selectedRequirementIds = Set.copyOf(plan.selectedRequirementIds());
        return new ApplicationContentGenerationRequest(
                request.profileVersionId(),
                request.profileChecksum(),
                request.jobChecksum(),
                request.evaluationChecksum(),
                request.jobTitle(),
                request.company(),
                request.jobDescription(),
                safe(request.facts()).stream().filter(fact -> selectedFactIds.contains(fact.id())).toList(),
                safe(request.jobRequirements()).stream()
                        .filter(requirement -> selectedRequirementIds.contains(requirement.id()))
                        .toList(),
                safe(request.questions()));
    }

    private <W, T> T call(
            String operation,
            String instructions,
            String input,
            ApplicationContentGenerationRequest source,
            String schemaName,
            JsonNode schema,
            Class<W> wireType,
            Function<W, T> decode) {
        long estimatedInputTokens = (input.length() + 3L) / 4L;
        if (input.length() > config.maxInputCharacters() || estimatedInputTokens > config.maxInputTokens()) {
            throw new IllegalArgumentException("CONTENT_GENERATION_INPUT_TOO_LARGE");
        }
        UUID executionId = tracker.started(operation, input, source);
        long started = System.nanoTime();
        boolean refusal = false;
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(config.model())
                    .instructions(instructions)
                    .input(input)
                    .store(false)
                    .maxOutputTokens(config.maxOutputTokens())
                    .reasoning(Reasoning.builder()
                            .effort(ReasoningEffort.of(config.reasoningEffort()))
                            .build())
                    .text(ResponseTextConfig.builder()
                            .format(responseFormat(schemaName, schema))
                            .build())
                    .build();
            var response = client.responses().create(params);
            refusal = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .anyMatch(content -> content.refusal().isPresent());
            if (refusal) throw new IllegalStateException("CONTENT_GENERATION_REFUSED");
            if (response.incompleteDetails().isPresent()) {
                throw new IllegalStateException("CONTENT_GENERATION_INCOMPLETE_RESPONSE");
            }
            String rawOutput = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(value -> value.text())
                    .collect(Collectors.joining());
            if (rawOutput.isBlank()) throw new IllegalStateException("CONTENT_GENERATION_EMPTY_RESPONSE");
            W wireOutput = parseAndValidate(rawOutput, schema, wireType);
            T output = decode.apply(wireOutput);
            long elapsed = System.nanoTime() - started;
            var usage = response.usage();
            Long inputTokens = usage.map(value -> value.inputTokens()).orElse(null);
            Long outputTokens = usage.map(value -> value.outputTokens()).orElse(null);
            Long totalTokens = usage.map(value -> value.totalTokens()).orElse(null);
            tracker.succeeded(
                    executionId,
                    response.id(),
                    rawOutput,
                    elapsed / 1_000_000,
                    inputTokens,
                    outputTokens,
                    totalTokens);
            metrics.provider(elapsed, inputTokens, outputTokens);
            return output;
        } catch (RuntimeException failure) {
            tracker.failed(executionId, failure, (System.nanoTime() - started) / 1_000_000, refusal);
            throw failure;
        }
    }

    static ResponseFormatTextJsonSchemaConfig responseFormat(String name, JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            throw new IllegalStateException("Application-generation response schema must be an object");
        }
        var schemaBuilder = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        schema.fields().forEachRemaining(field ->
                schemaBuilder.putAdditionalProperty(field.getKey(), JsonValue.fromJsonNode(field.getValue())));
        return ResponseFormatTextJsonSchemaConfig.builder()
                .name(name)
                .schema(schemaBuilder.build())
                .strict(true)
                .type(JsonValue.from("json_schema"))
                .build();
    }

    private <T> T parseAndValidate(String rawOutput, JsonNode schema, Class<T> type) {
        try {
            JsonNode output = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawOutput);
            if (output == null) throw new IllegalStateException("CONTENT_GENERATION_EMPTY_RESPONSE");
            ApplicationGenerationJsonSchemaValidator.validate(schema, output);
            return mapper.treeToValue(output, type);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("CONTENT_GENERATION_INVALID_RESPONSE", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize minimized generation input", exception);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static class PlanOutput {
        public List<UUID> selectedFactIds;
        public List<UUID> selectedRequirementIds;
        public List<String> orderedSkills;
        public List<String> unsupportedRequirements;
        public List<String> omissions;
        public String tone;
    }

    public static class WriteOutput {
        public List<ContentOutput> contents;
        public List<ClaimOutput> claims;
        public List<String> warnings;
        public List<String> unsupportedRequirements;
    }

    public static class ContentOutput {
        public GeneratedContentType type;
        public String key;
        public int order;
        public String text;
    }

    public static class ClaimOutput {
        public String contentKey;
        public String claimText;
        public ClaimType claimType;
        public List<UUID> factIds;
        public List<UUID> requirementIds;
        public String jobFieldReference;
    }
}
