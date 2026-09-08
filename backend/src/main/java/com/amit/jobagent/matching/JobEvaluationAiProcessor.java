package com.amit.jobagent.matching;

import com.amit.jobagent.ai.AiEvaluationProperties;
import com.amit.jobagent.ai.JobEvaluationLlmClient;
import com.amit.jobagent.ai.LlmJobEvaluationRequest;
import com.amit.jobagent.ai.LlmJobEvaluationResponse;
import com.amit.jobagent.ai.LlmJobEvaluationResult;
import com.amit.jobagent.job.JobMatchingView;
import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JobEvaluationAiProcessor {
    private final Optional<JobEvaluationLlmClient> client;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final AiEvaluationProperties config;
    private final PromptCatalog prompts;
    private final WeightedScoringService scoring = new WeightedScoringService();

    JobEvaluationAiProcessor(Optional<JobEvaluationLlmClient> client, ObjectMapper mapper, JdbcTemplate jdbc,
            AiEvaluationProperties config, PromptCatalog prompts) {
        this.client = client;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.config = config;
        this.prompts = prompts;
    }

    boolean available() {
        return client.isPresent();
    }

    void process(JobEvaluation evaluation, JobMatchingView job, PublishedProfileSnapshot profile,
            MatchingConfigurationResponse matching) {
        String input = input(job, profile);
        if (input.length() > config.maxInputCharacters()) {
            throw new IllegalArgumentException("AI evaluation input is too large");
        }
        UUID executionId = started();
        evaluation.running(executionId);
        try {
            LlmJobEvaluationResult result = client.orElseThrow().evaluate(new LlmJobEvaluationRequest(input));
            validate(result.output(), profile, job);
            var scores = result.output().scores;
            var weighted = scoring.calculate(new WeightedScoringService.Scores(scores.skills, scores.experience,
                    scores.role, scores.location, scores.domain, scores.compensation), matching, false,
                    "INSUFFICIENT_DATA".equals(result.output().status), result.output().confidence);
            evaluation.complete(result.output(), weighted, json(result.output()),
                    json(safe(result.output().matchedSkills)), json(safe(result.output().missingRequiredSkills)),
                    json(safe(result.output().missingPreferredSkills)), json(safe(result.output().risks)),
                    json(safe(result.output().questionsNeedingUserInput)), json(weighted.unassessedDimensions()));
            persistRequirements(evaluation.getId(), result.output().requirements);
            succeeded(executionId, result);
        } catch (Exception failure) {
            failed(executionId);
            evaluation.failed();
        }
    }

    private String input(JobMatchingView job, PublishedProfileSnapshot profile) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ObjectNode jobNode = root.putObject("job");
            jobNode.put("id", job.id().toString());
            put(jobNode, "company", job.company());
            put(jobNode, "title", job.title());
            put(jobNode, "location", job.location());
            put(jobNode, "workplaceType", String.valueOf(job.workplaceType()));
            put(jobNode, "employmentType", String.valueOf(job.employmentType()));
            put(jobNode, "description", job.description());

            ObjectNode candidate = root.putObject("candidate");
            JsonNode sourceProfile = profile.snapshot().path("profile");
            put(candidate, "professionalTitle", text(sourceProfile, "professionalTitle"));
            put(candidate, "currentCompany", text(sourceProfile, "currentCompany"));
            put(candidate, "currentLocation", text(sourceProfile, "currentLocation"));
            if (sourceProfile.has("totalExperienceMonths")) {
                candidate.set("totalExperienceMonths", sourceProfile.get("totalExperienceMonths"));
            }
            candidate.set("preferences", profile.snapshot().path("preferences"));
            var facts = candidate.putArray("verifiedResumeFacts");
            profile.snapshot().path("verifiedResumeFacts").forEach(source -> {
                ObjectNode fact = facts.addObject();
                for (String field : List.of("id", "category", "statement", "company", "startDate", "endDate",
                        "skillTags", "domainTags")) {
                    if (source.has(field) && !source.get(field).isNull()) fact.set(field, source.get(field));
                }
            });
            return prompts.inputTemplate.replace("{{SERIALIZED_INPUT}}", mapper.writeValueAsString(root));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize AI evaluation input", exception);
        }
    }

    private void validate(LlmJobEvaluationResponse value, PublishedProfileSnapshot profile, JobMatchingView job) {
        if (value == null || value.scores == null || value.status == null || value.summary == null) invalid();
        if (!Set.of("EVALUATED", "INSUFFICIENT_DATA").contains(value.status)
                || value.confidence < 0 || value.confidence > 100) invalid();
        List<Integer> scores = new ArrayList<>();
        scores.add(value.scores.skills); scores.add(value.scores.experience); scores.add(value.scores.role);
        scores.add(value.scores.location); scores.add(value.scores.domain); scores.add(value.scores.compensation);
        scores.stream().filter(Objects::nonNull).forEach(score -> { if (score < 0 || score > 100) invalid(); });

        Set<String> factIds = new HashSet<>();
        profile.snapshot().path("verifiedResumeFacts").forEach(fact -> factIds.add(fact.path("id").asText()));
        for (LlmJobEvaluationResponse.Requirement requirement : safe(value.requirements)) {
            if (requirement == null || requirement.requirementText == null || requirement.requirementType == null
                    || requirement.category == null || requirement.matchStatus == null
                    || requirement.jobEvidence == null) invalid();
            if (!factIds.containsAll(safe(requirement.candidateFactIds))) {
                throw new IllegalStateException("UNSUPPORTED_FACT_REFERENCE");
            }
            if (!requirement.jobEvidence.isBlank()
                    && (job.description() == null || !job.description().contains(requirement.jobEvidence))) {
                throw new IllegalStateException("UNSUPPORTED_JOB_EVIDENCE");
            }
        }
    }

    private void persistRequirements(UUID evaluationId, List<LlmJobEvaluationResponse.Requirement> requirements) {
        for (LlmJobEvaluationResponse.Requirement requirement : safe(requirements)) {
            jdbc.update("""
                    INSERT INTO job_agent.job_evaluation_requirement
                      (id,evaluation_id,requirement_text,requirement_type,category,match_status,
                       candidate_fact_ids,job_evidence,created_at)
                    VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?)
                    """, UUID.randomUUID(), evaluationId, requirement.requirementText, requirement.requirementType,
                    requirement.category, requirement.matchStatus, json(safe(requirement.candidateFactIds)),
                    requirement.jobEvidence, Timestamp.from(Instant.now()));
        }
    }

    private UUID started() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO job_agent.llm_execution
                  (id,operation_type,provider,model,prompt_name,prompt_version,prompt_checksum,
                   schema_version,schema_checksum,request_started_at,retry_count,status,refusal,created_at,cache_hit)
                VALUES (?,'JOB_EVALUATION','OPENAI',?,'job-evaluation','v1',?,'v1',?,?,0,'RUNNING',FALSE,?,FALSE)
                """, id, config.model(), prompts.promptChecksum, prompts.schemaChecksum, now, now);
        return id;
    }

    private void succeeded(UUID id, LlmJobEvaluationResult result) {
        jdbc.update("""
                UPDATE job_agent.llm_execution SET provider_response_id=?,request_completed_at=?,
                  latency_milliseconds=?,input_tokens=?,output_tokens=?,total_tokens=?,status='SUCCEEDED' WHERE id=?
                """, result.responseId(), Timestamp.from(Instant.now()), result.latencyMilliseconds(), result.inputTokens(),
                result.outputTokens(), result.totalTokens(), id);
    }

    private void failed(UUID id) {
        jdbc.update("""
                UPDATE job_agent.llm_execution SET request_completed_at=?,status='FAILED',safe_error_code=?,
                  safe_error_message=? WHERE id=?
                """, Timestamp.from(Instant.now()), "AI_PROVIDER_FAILURE", "AI evaluation provider request failed safely", id);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Could not serialize AI evaluation output", exception); }
    }

    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private static void put(ObjectNode node, String name, String value) { if (value != null) node.put(name, value); }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    private static void invalid() { throw new IllegalStateException("INVALID_AI_OUTPUT"); }
}
