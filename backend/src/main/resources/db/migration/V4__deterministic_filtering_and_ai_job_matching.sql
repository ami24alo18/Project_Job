CREATE TABLE matching_configuration (
 id UUID PRIMARY KEY, profile_id UUID NOT NULL UNIQUE REFERENCES candidate_profile(id) ON DELETE RESTRICT,
 skills_weight INTEGER NOT NULL DEFAULT 35, experience_weight INTEGER NOT NULL DEFAULT 20,
 role_weight INTEGER NOT NULL DEFAULT 15, location_weight INTEGER NOT NULL DEFAULT 15,
 domain_weight INTEGER NOT NULL DEFAULT 10, compensation_weight INTEGER NOT NULL DEFAULT 5,
 strong_apply_threshold INTEGER NOT NULL DEFAULT 85, apply_threshold INTEGER NOT NULL DEFAULT 75,
 manual_review_threshold INTEGER NOT NULL DEFAULT 60, maximum_allowed_experience_gap INTEGER NOT NULL DEFAULT 1,
 maximum_jobs_per_batch INTEGER NOT NULL DEFAULT 25, maximum_daily_ai_requests INTEGER NOT NULL DEFAULT 100,
 maximum_daily_input_tokens BIGINT NOT NULL DEFAULT 500000, ruleset_version VARCHAR(40) NOT NULL DEFAULT 'v1',
 record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK (skills_weight>=0 AND experience_weight>=0 AND role_weight>=0 AND location_weight>=0 AND domain_weight>=0 AND compensation_weight>=0),
 CHECK (skills_weight+experience_weight+role_weight+location_weight+domain_weight+compensation_weight=100),
 CHECK (strong_apply_threshold BETWEEN 0 AND 100 AND apply_threshold BETWEEN 0 AND 100 AND manual_review_threshold BETWEEN 0 AND 100),
 CHECK (strong_apply_threshold>apply_threshold AND apply_threshold>manual_review_threshold),
 CHECK (maximum_allowed_experience_gap>=0), CHECK (maximum_jobs_per_batch BETWEEN 1 AND 500),
 CHECK (maximum_daily_ai_requests BETWEEN 1 AND 10000), CHECK (maximum_daily_input_tokens BETWEEN 1 AND 1000000000)
);

CREATE TABLE job_rule_evaluation (
 id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES job_posting(id) ON DELETE RESTRICT,
 profile_version_id UUID NOT NULL REFERENCES candidate_profile_version(id) ON DELETE RESTRICT,
 ruleset_version VARCHAR(40) NOT NULL, result VARCHAR(30) NOT NULL CHECK(result IN ('PASS','FAIL','MANUAL_REVIEW')),
 reason_codes JSONB NOT NULL, safe_evidence JSONB NOT NULL, job_content_hash CHAR(64) NOT NULL,
 profile_snapshot_checksum CHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(job_id,profile_version_id,ruleset_version,job_content_hash,profile_snapshot_checksum)
);
CREATE INDEX idx_rule_evaluation_job_created ON job_rule_evaluation(job_id,created_at DESC);

CREATE TABLE llm_execution (
 id UUID PRIMARY KEY, provider VARCHAR(30) NOT NULL, model VARCHAR(100) NOT NULL, provider_response_id VARCHAR(200),
 prompt_name VARCHAR(100) NOT NULL, prompt_version VARCHAR(30) NOT NULL, prompt_checksum CHAR(64) NOT NULL,
 schema_version VARCHAR(30) NOT NULL, schema_checksum CHAR(64) NOT NULL, request_started_at TIMESTAMPTZ NOT NULL,
 request_completed_at TIMESTAMPTZ, latency_milliseconds BIGINT CHECK(latency_milliseconds>=0),
 input_tokens BIGINT CHECK(input_tokens>=0), output_tokens BIGINT CHECK(output_tokens>=0), total_tokens BIGINT CHECK(total_tokens>=0),
 retry_count INTEGER NOT NULL DEFAULT 0 CHECK(retry_count>=0), status VARCHAR(30) NOT NULL,
 safe_error_code VARCHAR(80), safe_error_message VARCHAR(500), refusal BOOLEAN NOT NULL DEFAULT FALSE,
 refusal_category VARCHAR(80), created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_llm_execution_usage ON llm_execution(request_started_at,status);

CREATE TABLE job_evaluation_run (
 id UUID PRIMARY KEY, trigger_type VARCHAR(20) NOT NULL CHECK(trigger_type IN ('MANUAL','N8N','RETRY')),
 status VARCHAR(30) NOT NULL CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','CANCELLED')),
 requested_count INTEGER NOT NULL DEFAULT 0, queued_count INTEGER NOT NULL DEFAULT 0,
 rule_filtered_count INTEGER NOT NULL DEFAULT 0, cache_hit_count INTEGER NOT NULL DEFAULT 0,
 succeeded_count INTEGER NOT NULL DEFAULT 0, needs_review_count INTEGER NOT NULL DEFAULT 0,
 failed_count INTEGER NOT NULL DEFAULT 0, budget_rejected_count INTEGER NOT NULL DEFAULT 0,
 started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, safe_error_code VARCHAR(80), safe_error_message VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL, CHECK(requested_count>=0 AND queued_count>=0 AND rule_filtered_count>=0 AND cache_hit_count>=0 AND succeeded_count>=0 AND needs_review_count>=0 AND failed_count>=0 AND budget_rejected_count>=0)
);
CREATE INDEX idx_job_evaluation_run_status_created ON job_evaluation_run(status,created_at DESC);

CREATE TABLE job_evaluation (
 id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES job_posting(id) ON DELETE RESTRICT,
 profile_version_id UUID NOT NULL REFERENCES candidate_profile_version(id) ON DELETE RESTRICT,
 rule_evaluation_id UUID REFERENCES job_rule_evaluation(id) ON DELETE RESTRICT,
 llm_execution_id UUID REFERENCES llm_execution(id) ON DELETE RESTRICT,
 run_id UUID REFERENCES job_evaluation_run(id) ON DELETE RESTRICT,
 status VARCHAR(30) NOT NULL CHECK(status IN ('QUEUED','RULE_FILTERED','AI_PENDING','AI_RUNNING','SUCCEEDED','NEEDS_REVIEW','FAILED','STALE','CANCELLED')),
 recommendation VARCHAR(30) CHECK(recommendation IN ('STRONG_APPLY','APPLY','MANUAL_REVIEW','SKIP')),
 overall_score INTEGER, skills_score INTEGER, experience_score INTEGER, role_score INTEGER, location_score INTEGER, domain_score INTEGER, compensation_score INTEGER, confidence INTEGER,
 matched_skills JSONB NOT NULL DEFAULT '[]', missing_required_skills JSONB NOT NULL DEFAULT '[]', missing_preferred_skills JSONB NOT NULL DEFAULT '[]',
 risks JSONB NOT NULL DEFAULT '[]', questions_needing_user_input JSONB NOT NULL DEFAULT '[]', summary VARCHAR(1000), structured_output_json JSONB,
 unassessed_dimensions JSONB NOT NULL DEFAULT '[]', job_content_hash CHAR(64) NOT NULL, profile_snapshot_checksum CHAR(64) NOT NULL,
 matching_configuration_checksum CHAR(64) NOT NULL, prompt_checksum CHAR(64) NOT NULL, schema_checksum CHAR(64) NOT NULL,
 evaluation_cache_key VARCHAR(64), stale BOOLEAN NOT NULL DEFAULT FALSE, safe_error_code VARCHAR(80), safe_error_message VARCHAR(500),
 record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 CHECK(overall_score IS NULL OR overall_score BETWEEN 0 AND 100), CHECK(confidence IS NULL OR confidence BETWEEN 0 AND 100),
 CHECK(skills_score IS NULL OR skills_score BETWEEN 0 AND 100), CHECK(experience_score IS NULL OR experience_score BETWEEN 0 AND 100),
 CHECK(role_score IS NULL OR role_score BETWEEN 0 AND 100), CHECK(location_score IS NULL OR location_score BETWEEN 0 AND 100),
 CHECK(domain_score IS NULL OR domain_score BETWEEN 0 AND 100), CHECK(compensation_score IS NULL OR compensation_score BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uq_job_evaluation_success_cache ON job_evaluation(evaluation_cache_key) WHERE evaluation_cache_key IS NOT NULL AND status IN ('SUCCEEDED','NEEDS_REVIEW','AI_PENDING','AI_RUNNING');
CREATE INDEX idx_job_evaluation_job_created ON job_evaluation(job_id,created_at DESC);
CREATE INDEX idx_job_evaluation_status_created ON job_evaluation(status,created_at DESC);
CREATE INDEX idx_job_evaluation_recommendation_score ON job_evaluation(recommendation,overall_score DESC);

CREATE TABLE job_evaluation_requirement (
 id UUID PRIMARY KEY, evaluation_id UUID NOT NULL REFERENCES job_evaluation(id) ON DELETE RESTRICT,
 requirement_text VARCHAR(1000) NOT NULL, requirement_type VARCHAR(20) NOT NULL,
 category VARCHAR(30) NOT NULL, match_status VARCHAR(20) NOT NULL, candidate_fact_ids JSONB NOT NULL,
 job_evidence VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_job_evaluation_requirement_eval ON job_evaluation_requirement(evaluation_id,id);

CREATE TABLE evaluation_feedback (
 id UUID PRIMARY KEY, evaluation_id UUID NOT NULL UNIQUE REFERENCES job_evaluation(id) ON DELETE RESTRICT,
 human_label VARCHAR(20) NOT NULL CHECK(human_label IN ('APPLY','MAYBE','SKIP')),
 was_recommendation_correct BOOLEAN NOT NULL, notes VARCHAR(1000), record_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
