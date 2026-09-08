package com.amit.jobagent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ApplicationLlmExecutionTracker {
    private final JdbcTemplate jdbc;
    private final ContentGenerationProperties config;
    private final ApplicationGenerationPromptCatalog prompts;

    ApplicationLlmExecutionTracker(
            JdbcTemplate jdbc, ContentGenerationProperties config, ApplicationGenerationPromptCatalog prompts) {
        this.jdbc = jdbc;
        this.config = config;
        this.prompts = prompts;
    }

    UUID started(String operation, String input, ApplicationContentGenerationRequest source) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO job_agent.llm_execution
                  (id,operation_type,provider,model,prompt_name,prompt_version,prompt_checksum,
                   schema_version,schema_checksum,request_started_at,retry_count,status,refusal,
                   created_at,input_checksum,cache_hit,source_job_checksum,source_profile_checksum,
                   source_evaluation_checksum)
                VALUES (?,?,?,?,?,?,?,?,?,?,0,'RUNNING',FALSE,?,?,FALSE,?,?,?)
                """, id, operation, config.provider().toUpperCase(Locale.ROOT), config.model(), operation.toLowerCase(Locale.ROOT),
                config.promptVersion(), prompts.promptChecksum, config.schemaVersion(), prompts.schemaChecksum,
                now, now, checksum(input), source.jobChecksum(), source.profileChecksum(), source.evaluationChecksum());
        return id;
    }

    void succeeded(
            UUID id, String providerResponseId, String output, long latencyMilliseconds,
            Long inputTokens, Long outputTokens, Long totalTokens) {
        jdbc.update("""
                UPDATE job_agent.llm_execution SET provider_response_id=?,request_completed_at=?,
                  latency_milliseconds=?,input_tokens=?,output_tokens=?,total_tokens=?,status='SUCCEEDED',
                  output_checksum=? WHERE id=?
                """, providerResponseId, Timestamp.from(Instant.now()), latencyMilliseconds, inputTokens, outputTokens,
                totalTokens, checksum(output), id);
    }

    void failed(UUID id, Throwable failure, long latencyMilliseconds, boolean refusal) {
        String code = ApplicationGenerationFailure.code(failure);
        jdbc.update("""
                UPDATE job_agent.llm_execution SET request_completed_at=?,latency_milliseconds=?,status='FAILED',
                  safe_error_code=?,safe_error_message=?,refusal=?,refusal_category=? WHERE id=?
                """, Timestamp.from(Instant.now()), latencyMilliseconds, limit(code, 80),
                ApplicationGenerationFailure.message(failure), refusal,
                refusal ? "PROVIDER_REFUSAL" : null, id);
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
