package com.amit.jobagent.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${job-agent.content-generation.enabled:false}' == 'true' && ('${job-agent.content-generation.provider:openai}' == 'ollama' || '${job-agent.content-generation.provider:openai}' == 'openrouter' || '${job-agent.content-generation.provider:openai}' == 'huggingface')")
class OllamaApplicationContentGenerator implements ApplicationContentGenerator {
    private final ContentGenerationProperties config;
    private final OllamaContentGenerationProperties ollama;
    private final OpenRouterContentGenerationProperties openRouter;
    private final HuggingFaceContentGenerationProperties huggingFace;
    private final ObjectMapper mapper;
    private final ApplicationGenerationPromptCatalog prompts;
    private final ApplicationLlmExecutionTracker tracker;
    private final ApplicationGenerationMetrics metrics;
    private final HttpClient client;

    @Autowired
    OllamaApplicationContentGenerator(
            ContentGenerationProperties config,
            OllamaContentGenerationProperties ollama,
            OpenRouterContentGenerationProperties openRouter,
            HuggingFaceContentGenerationProperties huggingFace,
            ObjectMapper mapper,
            ApplicationGenerationPromptCatalog prompts,
            ApplicationLlmExecutionTracker tracker,
            ApplicationGenerationMetrics metrics) {
        this(config, ollama, openRouter, huggingFace, mapper, prompts, tracker, metrics,
                HttpClient.newBuilder()
                        .connectTimeout(providerTimeout(config, ollama, openRouter, huggingFace))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    OllamaApplicationContentGenerator(
            ContentGenerationProperties config,
            OllamaContentGenerationProperties ollama,
            OpenRouterContentGenerationProperties openRouter,
            HuggingFaceContentGenerationProperties huggingFace,
            ObjectMapper mapper,
            ApplicationGenerationPromptCatalog prompts,
            ApplicationLlmExecutionTracker tracker,
            ApplicationGenerationMetrics metrics,
            HttpClient client) {
        this.config = config;
        this.ollama = ollama;
        this.openRouter = openRouter;
        this.huggingFace = huggingFace;
        this.mapper = mapper;
        this.prompts = prompts;
        this.tracker = tracker;
        this.metrics = metrics;
        this.client = client;
    }

    @Override
    public TailoringPlan plan(ApplicationContentGenerationRequest request) {
        return localPlan(request);
    }

    /**
     * Local CPU inference is reserved for the valuable writing pass. Evidence selection is
     * deterministic, broad enough for a complete resume, and ranked against the job text.
     */
    private static TailoringPlan localPlan(ApplicationContentGenerationRequest request) {
        String jobText = normalize(String.join(" ",
                request.jobTitle(), request.company(), request.jobDescription(),
                request.jobRequirements().stream()
                        .map(requirement -> requirement.text() + " " + requirement.jobEvidence())
                        .toList().toString()));
        Map<UUID, Integer> sourceOrder = new LinkedHashMap<>();
        for (int index = 0; index < request.facts().size(); index++) {
            sourceOrder.put(request.facts().get(index).id(), index);
        }
        List<VerifiedFactSnapshot> selected = request.facts().stream()
                .sorted(java.util.Comparator
                        .comparingInt((VerifiedFactSnapshot fact) -> relevance(fact, jobText)).reversed()
                        .thenComparingInt(fact -> sourceOrder.get(fact.id())))
                .limit(30)
                .sorted(java.util.Comparator.comparingInt(fact -> sourceOrder.get(fact.id())))
                .toList();
        Set<UUID> selectedIds = selected.stream().map(VerifiedFactSnapshot::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> skills = selected.stream()
                .flatMap(fact -> fact.skillTags().stream())
                .filter(skill -> skill != null && !skill.isBlank())
                .distinct()
                .sorted(java.util.Comparator
                        .comparing((String skill) -> !jobText.contains(normalize(skill)))
                        .thenComparing(String::compareToIgnoreCase))
                .limit(20)
                .toList();
        List<UUID> requirements = request.jobRequirements().stream()
                .map(JobRequirementSnapshot::id)
                .limit(15)
                .toList();
        return new TailoringPlan(
                List.copyOf(selectedIds), requirements, skills, List.of(), List.of(), "concise-professional");
    }

    private static int relevance(VerifiedFactSnapshot fact, String jobText) {
        String evidence = normalize(String.join(" ",
                java.util.Objects.toString(fact.statement(), ""),
                java.util.Objects.toString(fact.company(), ""),
                fact.skillTags().toString(), fact.domainTags().toString()));
        int score = 0;
        for (String token : evidence.split("[^a-z0-9+#.]+")) {
            if (token.length() >= 3 && jobText.contains(token)) score++;
        }
        if ("EMPLOYMENT".equals(fact.category()) || "PROJECT".equals(fact.category())) score += 2;
        if (evidence.matches(".*\\b\\d+(?:[.,]\\d+)?%?.*")) score += 2;
        return score;
    }

    @Override
    public GeneratedApplicationContent generate(
            ApplicationContentGenerationRequest request, TailoringPlan plan) {
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
        var selectedSource = OpenAiApplicationContentGenerator.selectedSource(request, plan);
        input.put("validatedPlan", plan);
        input.put("source", selectedSource);
        if (!validationCodes.isEmpty()) input.put("validationErrors", validationCodes);
        if ("v2".equals(config.schemaVersion())) {
            return call(
                    operation,
                    prompts.writeSystem,
                    json(input),
                    selectedSource,
                    prompts.writeSchemaDocument,
                    CompactWriteOutput.class,
                    output -> compactContent(output, request, plan));
        }
        return call(
                operation,
                prompts.writeSystem,
                json(input),
                selectedSource,
                prompts.writeSchemaDocument,
                OpenAiApplicationContentGenerator.WriteOutput.class,
                output -> {
                    var content = safe(output.contents).stream()
                            .map(value -> new GeneratedContentItem(value.type, value.key, value.order, value.text))
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
                            content, claims, safe(output.warnings), missingRequirements(request));
                });
    }

    private static GeneratedApplicationContent compactContent(
            CompactWriteOutput output,
            ApplicationContentGenerationRequest request,
            TailoringPlan plan) {
        var contents = new java.util.ArrayList<GeneratedContentItem>();
        var claims = new java.util.ArrayList<GeneratedClaimAtom>();
        int order = 0;

        if (output.summary != null && output.summary.text != null && !output.summary.text.isBlank()) {
            contents.add(new GeneratedContentItem(
                    GeneratedContentType.PROFESSIONAL_SUMMARY, "summary", order++, output.summary.text));
            claims.add(new GeneratedClaimAtom(
                    "summary", output.summary.text, ClaimType.CANDIDATE_FACT,
                    safe(output.summary.factIds), List.of(), null));
        }

        List<VerifiedFactSnapshot> evidence = orderedEvidence(request, plan);
        Map<String, VerifiedFactSnapshot> skills = groundedSkills(safe(output.skills), evidence);
        if (skills.isEmpty()) {
            skills = groundedSkills(plan.orderedSkills(), evidence);
        }
        if (!skills.isEmpty()) {
            String text = String.join(", ", skills.keySet());
            contents.add(new GeneratedContentItem(
                    GeneratedContentType.SKILL_SECTION, "skills", order++, text));
            for (Map.Entry<String, VerifiedFactSnapshot> skill : skills.entrySet()) {
                claims.add(new GeneratedClaimAtom(
                        "skills", skill.getKey(), ClaimType.CANDIDATE_FACT,
                        List.of(skill.getValue().id()), List.of(), null));
            }
        }

        int bulletIndex = 0;
        for (CompactBullet bullet : safe(output.bullets)) {
            if (bullet == null || bullet.text == null || bullet.text.isBlank()) continue;
            String key = "bullet-" + bulletIndex++;
            GeneratedContentType type = switch (normalize(bullet.kind)) {
                case "project" -> GeneratedContentType.PROJECT_BULLET;
                case "education" -> GeneratedContentType.EDUCATION_SECTION;
                default -> GeneratedContentType.EXPERIENCE_BULLET;
            };
            contents.add(new GeneratedContentItem(type, key, order++, bullet.text));
            claims.add(new GeneratedClaimAtom(
                    key, bullet.text, ClaimType.CANDIDATE_FACT,
                    bullet.factId == null ? List.of() : List.of(bullet.factId), List.of(), null));
        }

        List<VerifiedFactSnapshot> selected = selectedFacts(request, plan);
        String coverKey = "cover-letter";
        String opening = "I am excited to apply for the " + request.jobTitle()
                + " role at " + request.company() + ".";
        String evidenceParagraph = selected.isEmpty() ? "" : "\n\nRelevant experience: "
                + selected.stream().limit(3).map(VerifiedFactSnapshot::statement)
                .collect(java.util.stream.Collectors.joining(" "));
        String closing = "\n\nI would welcome the opportunity to discuss this role.";
        contents.add(new GeneratedContentItem(
                GeneratedContentType.COVER_LETTER, coverKey, order++, opening + evidenceParagraph + closing));
        addJobOpeningClaims(claims, coverKey, request);
        if (!selected.isEmpty()) {
            VerifiedFactSnapshot first = selected.getFirst();
            claims.add(new GeneratedClaimAtom(
                    coverKey, "Relevant experience: " + first.statement(), ClaimType.CANDIDATE_FACT,
                    List.of(first.id()), List.of(), null));
            selected.stream().skip(1).limit(2).forEach(fact -> claims.add(new GeneratedClaimAtom(
                    coverKey, fact.statement(), ClaimType.CANDIDATE_FACT,
                    List.of(fact.id()), List.of(), null)));
        }
        claims.add(new GeneratedClaimAtom(
                coverKey, "I would welcome the opportunity to discuss this role.", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));

        String messageKey = "recruiter-message";
        String message = "Hello, I am interested in the " + request.jobTitle()
                + " role at " + request.company() + ".";
        contents.add(new GeneratedContentItem(
                GeneratedContentType.RECRUITER_MESSAGE, messageKey, order, message));
        claims.add(new GeneratedClaimAtom(
                messageKey, "Hello, I am interested in the", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        claims.add(new GeneratedClaimAtom(
                messageKey, request.jobTitle(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.title"));
        claims.add(new GeneratedClaimAtom(
                messageKey, "role at", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        claims.add(new GeneratedClaimAtom(
                messageKey, request.company(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.company"));

        return new GeneratedApplicationContent(
                List.copyOf(contents),
                List.copyOf(claims),
                safe(output.warnings),
                missingRequirements(request));
    }

    private static List<VerifiedFactSnapshot> orderedEvidence(
            ApplicationContentGenerationRequest request, TailoringPlan plan) {
        Map<UUID, VerifiedFactSnapshot> evidence = new LinkedHashMap<>();
        selectedFacts(request, plan).forEach(fact -> evidence.putIfAbsent(fact.id(), fact));
        request.facts().stream()
                .filter(java.util.Objects::nonNull)
                .forEach(fact -> evidence.putIfAbsent(fact.id(), fact));
        return List.copyOf(evidence.values());
    }

    /**
     * The model ranks and names skills, but the application owns provenance. A returned skill is
     * retained only when an immutable resume fact explicitly supports it, and the backend attaches
     * that exact fact ID. This deliberately turns "Kafka requested by the JD, SQS in the resume"
     * into a gap rather than fabricated Kafka experience.
     */
    static Map<String, VerifiedFactSnapshot> groundedSkills(
            List<String> candidates, List<VerifiedFactSnapshot> evidence) {
        Map<String, VerifiedFactSnapshot> result = new LinkedHashMap<>();
        for (String candidate : safe(candidates)) {
            if (candidate == null || candidate.isBlank()) continue;
            String display = candidate.trim();
            String key = normalize(display);
            if (key.isBlank() || result.keySet().stream().anyMatch(existing -> normalize(existing).equals(key))) {
                continue;
            }
            evidence.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(fact -> supportsSkill(fact, key))
                    .findFirst()
                    .ifPresent(fact -> result.put(display, fact));
        }
        return result;
    }

    private static boolean supportsSkill(VerifiedFactSnapshot fact, String normalizedSkill) {
        if (fact.skillTags() != null && fact.skillTags().stream()
                .filter(java.util.Objects::nonNull)
                .map(OllamaApplicationContentGenerator::normalize)
                .anyMatch(normalizedSkill::equals)) {
            return true;
        }
        StringBuilder source = new StringBuilder();
        appendEvidence(source, fact.statement());
        appendEvidence(source, fact.company());
        if (fact.domainTags() != null) fact.domainTags().forEach(value -> appendEvidence(source, value));
        String haystack = " " + canonicalWords(source.toString()) + " ";
        String needle = " " + canonicalWords(normalizedSkill) + " ";
        return needle.length() > 2 && haystack.contains(needle);
    }

    private static void appendEvidence(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) target.append(' ').append(value);
    }

    private static String canonicalWords(String value) {
        return normalize(value).replaceAll("[^a-z0-9+#.]+", " ").trim();
    }

    private static List<VerifiedFactSnapshot> selectedFacts(
            ApplicationContentGenerationRequest request, TailoringPlan plan) {
        Map<UUID, VerifiedFactSnapshot> facts = request.facts().stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        VerifiedFactSnapshot::id, Function.identity(), (first, ignored) -> first));
        List<VerifiedFactSnapshot> selected = plan.selectedFactIds().stream()
                .map(facts::get)
                .filter(java.util.Objects::nonNull)
                .filter(fact -> fact.statement() != null && !fact.statement().isBlank())
                .limit(6)
                .toList();
        return selected.isEmpty()
                ? request.facts().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(fact -> fact.statement() != null && !fact.statement().isBlank())
                        .limit(3)
                        .toList()
                : selected;
    }

    /**
     * Missing requirements are a deterministic evaluation result, not creative model output.
     * This prevents a small model from reporting the same skill as both matched and missing, and
     * prevents it from inventing a gap that was never present in the JD.
     */
    static List<String> missingRequirements(ApplicationContentGenerationRequest request) {
        if (request == null || request.jobRequirements() == null) return List.of();
        return request.jobRequirements().stream()
                .filter(requirement -> "MISSING".equalsIgnoreCase(requirement.matchStatus()))
                .map(JobRequirementSnapshot::text)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private <W, T> T call(
            String operation,
            String instructions,
            String input,
            ApplicationContentGenerationRequest source,
            JsonNode schema,
            Class<W> wireType,
            Function<W, T> decode) {
        long estimatedInputTokens = (instructions.length() + input.length() + schema.toString().length() + 3L) / 4L;
        if (input.length() > config.maxInputCharacters() || estimatedInputTokens > config.maxInputTokens()) {
            throw new IllegalArgumentException("CONTENT_GENERATION_INPUT_TOO_LARGE");
        }
        UUID executionId = tracker.started(operation, input, source);
        long started = System.nanoTime();
        try {
            JsonNode response = send(instructions, input, schema);
            String rawOutput = responseContent(response);
            if (rawOutput.isBlank()) throw new IllegalStateException("CONTENT_GENERATION_EMPTY_RESPONSE");
            if (responseTruncated(response)) throw new IllegalStateException("CONTENT_GENERATION_OUTPUT_TRUNCATED");
            W wireOutput = parseAndValidate(rawOutput, schema, wireType);
            T output = decode.apply(wireOutput);
            long elapsed = System.nanoTime() - started;
            Long inputTokens = isHostedProvider()
                    ? positiveLong(response.path("usage"), "prompt_tokens")
                    : positiveLong(response, "prompt_eval_count");
            Long outputTokens = isHostedProvider()
                    ? positiveLong(response.path("usage"), "completion_tokens")
                    : positiveLong(response, "eval_count");
            Long totalTokens = inputTokens == null || outputTokens == null ? null : inputTokens + outputTokens;
            String servedModel = response.path("model").asText(null);
            tracker.succeeded(executionId, servedModel, rawOutput, elapsed / 1_000_000,
                    inputTokens, outputTokens, totalTokens);
            metrics.provider(elapsed, inputTokens, outputTokens);
            return output;
        } catch (RuntimeException failure) {
            tracker.failed(executionId, failure, (System.nanoTime() - started) / 1_000_000, false);
            throw failure;
        }
    }

    private JsonNode send(String instructions, String input, JsonNode schema) {
        return switch (config.provider()) {
            case "openrouter" -> sendOpenRouter(instructions, input, schema);
            case "huggingface" -> sendHuggingFace(instructions, input, schema);
            default -> sendOllama(instructions, input, schema);
        };
    }

    private JsonNode sendOllama(String instructions, String input, JsonNode schema) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)));
        body.put("stream", false);
        body.put("think", false);
        body.put("format", schema);
        body.put("keep_alive", ollama.keepAlive());
        body.put("options", Map.of(
                "temperature", 0,
                "num_ctx", ollama.contextWindow(),
                "num_predict", config.maxOutputTokens()));
        HttpRequest request = HttpRequest.newBuilder(ollama.chatEndpoint())
                .timeout(ollama.timeout())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                .build();
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= ollama.maxRetries(); attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return mapper.readTree(response.body());
                }
                lastFailure = new IllegalStateException("OLLAMA_HTTP_" + response.statusCode());
                if (response.statusCode() < 500) throw lastFailure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OLLAMA_REQUEST_INTERRUPTED", exception);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException("OLLAMA_UNAVAILABLE", exception);
            }
        }
        throw lastFailure == null ? new IllegalStateException("OLLAMA_UNAVAILABLE") : lastFailure;
    }

    private JsonNode sendOpenRouter(String instructions, String input, JsonNode schema) {
        String key = System.getenv("OPENROUTER_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY_MISSING");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)));
        body.put("temperature", 0);
        body.put("max_tokens", config.maxOutputTokens());
        body.put("reasoning", openRouterReasoning(config.reasoningEffort()));
        body.put("provider", Map.of(
                "data_collection", openRouter.allowDataCollection() ? "allow" : "deny",
                "require_parameters", true));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "job_agent_output", "strict", true, "schema", schema)));
        HttpRequest request = HttpRequest.newBuilder(openRouter.chatEndpoint())
                .timeout(openRouter.timeout())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.trim())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("HTTP-Referer", openRouter.httpReferer())
                .header("X-OpenRouter-Title", openRouter.appTitle())
                .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                .build();
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= openRouter.maxRetries(); attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return mapper.readTree(response.body());
                }
                lastFailure = new IllegalStateException("OPENROUTER_HTTP_" + response.statusCode());
                if (response.statusCode() < 500 && response.statusCode() != 429) throw lastFailure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OPENROUTER_REQUEST_INTERRUPTED", exception);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException("OPENROUTER_UNAVAILABLE", exception);
            }
        }
        throw lastFailure == null ? new IllegalStateException("OPENROUTER_UNAVAILABLE") : lastFailure;
    }

    private JsonNode sendHuggingFace(String instructions, String input, JsonNode schema) {
        String key = System.getenv("HF_TOKEN");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("HF_TOKEN_MISSING");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)));
        body.put("stream", false);
        body.put("temperature", 0);
        body.put("max_tokens", config.maxOutputTokens());
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "job_agent_output", "strict", true, "schema", schema)));
        HttpRequest request = HttpRequest.newBuilder(huggingFace.chatEndpoint())
                .timeout(huggingFace.timeout())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.trim())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                .build();
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= huggingFace.maxRetries(); attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return mapper.readTree(response.body());
                }
                lastFailure = new IllegalStateException("HUGGINGFACE_HTTP_" + response.statusCode());
                if (response.statusCode() < 500 && response.statusCode() != 429) throw lastFailure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("HUGGINGFACE_REQUEST_INTERRUPTED", exception);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException("HUGGINGFACE_UNAVAILABLE", exception);
            }
        }
        throw lastFailure == null ? new IllegalStateException("HUGGINGFACE_UNAVAILABLE") : lastFailure;
    }

    static Map<String, Object> openRouterReasoning(String effort) {
        String normalized = effort == null ? "none" : effort.trim().toLowerCase(Locale.ROOT);
        // OpenRouter models may enable reasoning by default. Omitting this object therefore does
        // not mean "off" and can exhaust max_tokens before a final message is produced.
        return Map.of("effort", normalized.isBlank() ? "none" : normalized, "exclude", true);
    }

    private String responseContent(JsonNode response) {
        if (isHostedProvider()) {
            return response.path("choices").path(0).path("message").path("content").asText("");
        }
        return response.path("message").path("content").asText("");
    }

    private boolean responseTruncated(JsonNode response) {
        if (isHostedProvider()) {
            return "length".equalsIgnoreCase(response.path("choices").path(0).path("finish_reason").asText(""));
        }
        return "length".equalsIgnoreCase(response.path("done_reason").asText(""));
    }

    private boolean isHostedProvider() {
        return "openrouter".equals(config.provider()) || "huggingface".equals(config.provider());
    }

    private static java.time.Duration providerTimeout(
            ContentGenerationProperties config,
            OllamaContentGenerationProperties ollama,
            OpenRouterContentGenerationProperties openRouter,
            HuggingFaceContentGenerationProperties huggingFace) {
        return switch (config.provider()) {
            case "openrouter" -> openRouter.timeout();
            case "huggingface" -> huggingFace.timeout();
            default -> ollama.timeout();
        };
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

    private static Long positiveLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    static final class CompactWriteOutput {
        public CompactSummary summary;
        public List<String> skills;
        public List<CompactBullet> bullets;
        public List<String> warnings;
    }

    static final class CompactSummary {
        public String text;
        public List<UUID> factIds;
    }

    static final class CompactBullet {
        public String kind;
        public UUID factId;
        public String text;
    }

    /**
     * A local model may return a syntactically valid plan containing an ID or skill that was not
     * supplied as evidence. Keep its ranking decisions, but deterministically remove anything
     * outside the request's allow-list before the shared provenance validator sees the plan.
     */
    private static TailoringPlan boundedPlan(
            OpenAiApplicationContentGenerator.PlanOutput output,
            ApplicationContentGenerationRequest request) {
        Set<UUID> allowedFacts = request.facts().stream()
                .map(VerifiedFactSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> allowedRequirements = request.jobRequirements().stream()
                .map(JobRequirementSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        List<UUID> selectedFacts = new LinkedHashSet<>(safe(output.selectedFactIds)).stream()
                .filter(allowedFacts::contains)
                .toList();
        List<UUID> selectedRequirements = new LinkedHashSet<>(safe(output.selectedRequirementIds)).stream()
                .filter(allowedRequirements::contains)
                .toList();
        Map<String, String> allowedSkills = new LinkedHashMap<>();
        request.facts().stream()
                .filter(fact -> selectedFacts.contains(fact.id()))
                .flatMap(fact -> fact.skillTags().stream())
                .forEach(skill -> allowedSkills.putIfAbsent(normalize(skill), skill));
        List<String> orderedSkills = new LinkedHashSet<>(safe(output.orderedSkills)).stream()
                .map(skill -> allowedSkills.get(normalize(skill)))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new TailoringPlan(
                selectedFacts,
                selectedRequirements,
                orderedSkills,
                safe(output.unsupportedRequirements),
                safe(output.omissions),
                output.tone == null ? "concise-professional" : output.tone);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static boolean meetsMinimumDraftQuality(GeneratedApplicationContent generated) {
        if (generated == null || generated.contents() == null || generated.contents().size() < 4) return false;
        boolean summary = false;
        boolean evidenceSection = false;
        boolean coverLetter = false;
        for (GeneratedContentItem content : generated.contents()) {
            if (content == null || content.type() == null || content.text() == null || content.text().isBlank()) continue;
            if (content.type() == GeneratedContentType.PROFESSIONAL_SUMMARY) summary = true;
            if (content.type() == GeneratedContentType.EXPERIENCE_BULLET
                    || content.type() == GeneratedContentType.PROJECT_BULLET
                    || content.type() == GeneratedContentType.EDUCATION_SECTION) evidenceSection = true;
            if (content.type() == GeneratedContentType.COVER_LETTER && content.text().length() >= 120) {
                coverLetter = true;
            }
        }
        return summary && evidenceSection && coverLetter;
    }

    /**
     * The shared validator deliberately rejects natural-sounding prose containing even one token
     * outside immutable evidence. After the model's single repair attempt, replace the response
     * with a complete, deterministic draft built from exact evidence atoms. Keeping the model's
     * broken section selection here used to produce fragments such as "I am excited. Java ..." or
     * even a cover letter with no resume sections. The replacement is intentionally conservative,
     * but it is readable, complete and cannot invent candidate experience.
     */
    static GeneratedApplicationContent groundRepair(
            GeneratedApplicationContent generated,
            ApplicationContentGenerationRequest request,
            TailoringPlan plan) {
        Map<UUID, VerifiedFactSnapshot> facts = request.facts().stream()
                .collect(java.util.stream.Collectors.toMap(VerifiedFactSnapshot::id, value -> value));
        List<VerifiedFactSnapshot> selected = plan.selectedFactIds().stream()
                .map(facts::get)
                .filter(java.util.Objects::nonNull)
                .filter(fact -> fact.statement() != null && !fact.statement().isBlank())
                .limit(12)
                .toList();
        if (selected.isEmpty()) {
            selected = request.facts().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(fact -> fact.statement() != null && !fact.statement().isBlank())
                    .limit(6)
                    .toList();
        }
        var groundedContents = new java.util.ArrayList<GeneratedContentItem>();
        var groundedClaims = new java.util.ArrayList<GeneratedClaimAtom>();
        int order = 0;

        if (!selected.isEmpty()) {
            String key = "safe-professional-summary";
            String text = "Relevant experience: " + selected.stream().limit(3)
                    .map(VerifiedFactSnapshot::statement)
                    .collect(java.util.stream.Collectors.joining(" "));
            groundedContents.add(new GeneratedContentItem(
                    GeneratedContentType.PROFESSIONAL_SUMMARY, key, order++, text));
            VerifiedFactSnapshot first = selected.getFirst();
            groundedClaims.add(new GeneratedClaimAtom(
                    key, "Relevant experience: " + first.statement(), ClaimType.CANDIDATE_FACT,
                    List.of(first.id()), List.of(), null));
            selected.stream().skip(1).limit(2).forEach(fact -> groundedClaims.add(
                    new GeneratedClaimAtom(key, fact.statement(), ClaimType.CANDIDATE_FACT,
                            List.of(fact.id()), List.of(), null)));
        }

        var skills = new LinkedHashMap<String, VerifiedFactSnapshot>();
        selected.forEach(fact -> fact.skillTags().forEach(skill -> {
            if (skill != null && !skill.isBlank()) skills.putIfAbsent(normalize(skill), fact);
        }));
        List<String> orderedSkills = plan.orderedSkills().stream()
                .map(skill -> skills.get(normalize(skill)) == null ? null : skill.trim())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (orderedSkills.isEmpty()) {
            orderedSkills = skills.values().stream()
                    .flatMap(fact -> fact.skillTags().stream())
                    .filter(skill -> skill != null && !skill.isBlank())
                    .distinct()
                    .limit(20)
                    .toList();
        }
        if (!orderedSkills.isEmpty()) {
            String key = "safe-skills";
            groundedContents.add(new GeneratedContentItem(
                    GeneratedContentType.SKILL_SECTION, key, order++, String.join(", ", orderedSkills)));
            for (String skill : orderedSkills) {
                VerifiedFactSnapshot fact = skills.get(normalize(skill));
                if (fact != null) groundedClaims.add(new GeneratedClaimAtom(
                        key, skill, ClaimType.CANDIDATE_FACT, List.of(fact.id()), List.of(), null));
            }
        }

        int factIndex = 0;
        for (VerifiedFactSnapshot fact : selected) {
            GeneratedContentType type = "PROJECT".equals(fact.category())
                    ? GeneratedContentType.PROJECT_BULLET
                    : "EDUCATION".equals(fact.category())
                            ? GeneratedContentType.EDUCATION_SECTION
                            : GeneratedContentType.EXPERIENCE_BULLET;
            String key = "safe-fact-" + factIndex++;
            groundedContents.add(new GeneratedContentItem(type, key, order++, fact.statement()));
            groundedClaims.add(new GeneratedClaimAtom(
                    key, fact.statement(), ClaimType.CANDIDATE_FACT,
                    List.of(fact.id()), List.of(), null));
        }

        String coverKey = "safe-cover-letter";
        String opening = "I am excited to apply for the " + request.jobTitle()
                + " role at " + request.company() + ".";
        String evidenceParagraph = selected.isEmpty() ? "" : "\n\nRelevant experience: "
                + selected.stream().limit(3).map(VerifiedFactSnapshot::statement)
                .collect(java.util.stream.Collectors.joining(" "));
        String closing = "\n\nI would welcome the opportunity to discuss this role.";
        groundedContents.add(new GeneratedContentItem(
                GeneratedContentType.COVER_LETTER, coverKey, order++, opening + evidenceParagraph + closing));
        addJobOpeningClaims(groundedClaims, coverKey, request);
        if (!selected.isEmpty()) {
            VerifiedFactSnapshot first = selected.getFirst();
            groundedClaims.add(new GeneratedClaimAtom(
                    coverKey, "Relevant experience: " + first.statement(), ClaimType.CANDIDATE_FACT,
                    List.of(first.id()), List.of(), null));
            selected.stream().skip(1).limit(2).forEach(fact -> groundedClaims.add(
                    new GeneratedClaimAtom(coverKey, fact.statement(), ClaimType.CANDIDATE_FACT,
                            List.of(fact.id()), List.of(), null)));
        }
        groundedClaims.add(new GeneratedClaimAtom(
                coverKey, "I would welcome the opportunity to discuss this role.", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));

        String messageKey = "safe-recruiter-message";
        String message = "Hello, I am interested in the " + request.jobTitle()
                + " role at " + request.company() + ".";
        groundedContents.add(new GeneratedContentItem(
                GeneratedContentType.RECRUITER_MESSAGE, messageKey, order, message));
        groundedClaims.add(new GeneratedClaimAtom(
                messageKey, "Hello, I am interested in the", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        groundedClaims.add(new GeneratedClaimAtom(
                messageKey, request.jobTitle(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.title"));
        groundedClaims.add(new GeneratedClaimAtom(
                messageKey, "role at", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        groundedClaims.add(new GeneratedClaimAtom(
                messageKey, request.company(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.company"));

        var warnings = new java.util.ArrayList<>(safe(generated.warnings()));
        warnings.add("MODEL_GROUNDED_FALLBACK");
        warnings.add("MODEL_OUTPUT_REPLACED_WITH_EVIDENCE_GROUNDED_DRAFT");
        return new GeneratedApplicationContent(
                List.copyOf(groundedContents),
                List.copyOf(groundedClaims),
                List.copyOf(new LinkedHashSet<>(warnings)),
                safe(generated.unsupportedRequirements()));
    }

    private static void addJobOpeningClaims(
            List<GeneratedClaimAtom> claims,
            String contentKey,
            ApplicationContentGenerationRequest request) {
        claims.add(new GeneratedClaimAtom(
                contentKey, "I am excited to apply for the", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        claims.add(new GeneratedClaimAtom(
                contentKey, request.jobTitle(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.title"));
        claims.add(new GeneratedClaimAtom(
                contentKey, "role at", ClaimType.NON_FACTUAL,
                List.of(), List.of(), null));
        claims.add(new GeneratedClaimAtom(
                contentKey, request.company(), ClaimType.JOB_REFERENCE,
                List.of(), List.of(), "job.company"));
    }

    private static List<GeneratedClaimAtom> groundClaim(
            GeneratedClaimAtom claim,
            String contentKey,
            ApplicationContentGenerationRequest request,
            Map<UUID, VerifiedFactSnapshot> facts,
            Map<UUID, JobRequirementSnapshot> requirements) {
        if (claim == null) return List.of();
        var result = new java.util.ArrayList<GeneratedClaimAtom>();
        for (UUID factId : safe(claim.factIds())) {
            VerifiedFactSnapshot fact = facts.get(factId);
            if (fact != null && fact.statement() != null && !fact.statement().isBlank()) {
                result.add(new GeneratedClaimAtom(
                        contentKey, fact.statement(), ClaimType.CANDIDATE_FACT,
                        List.of(factId), List.of(), null));
            }
        }
        for (UUID requirementId : safe(claim.requirementIds())) {
            JobRequirementSnapshot requirement = requirements.get(requirementId);
            if (requirement != null) {
                String evidence = firstNonBlank(requirement.jobEvidence(), requirement.text());
                if (evidence != null) {
                    result.add(new GeneratedClaimAtom(
                            contentKey, evidence, ClaimType.JOB_REFERENCE,
                            List.of(), List.of(requirementId), null));
                }
            }
        }
        for (String field : parseFields(claim.jobFieldReference())) {
            String evidence = switch (field) {
                case "job.title" -> request.jobTitle();
                case "job.company" -> request.company();
                default -> null;
            };
            if (evidence != null && !evidence.isBlank()) {
                result.add(new GeneratedClaimAtom(
                        contentKey, evidence, ClaimType.JOB_REFERENCE,
                        List.of(), List.of(), field));
            }
        }
        if (result.isEmpty() && claim.claimType() == ClaimType.NON_FACTUAL) {
            result.add(new GeneratedClaimAtom(
                    contentKey, "I am excited.", ClaimType.NON_FACTUAL,
                    List.of(), List.of(), null));
        }
        return result;
    }

    private static List<String> parseFields(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> value.equals("job.title") || value.equals("job.company"))
                .distinct()
                .toList();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null || second.isBlank() ? null : second;
    }
}
