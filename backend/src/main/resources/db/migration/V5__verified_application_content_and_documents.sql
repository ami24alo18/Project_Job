ALTER TABLE llm_execution ADD COLUMN operation_type VARCHAR(40) NOT NULL DEFAULT 'JOB_EVALUATION';
ALTER TABLE llm_execution ADD COLUMN input_checksum CHAR(64);
ALTER TABLE llm_execution ADD COLUMN output_checksum CHAR(64);
ALTER TABLE llm_execution ADD COLUMN cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE application_package (
 id UUID PRIMARY KEY, candidate_id UUID NOT NULL REFERENCES candidate_profile(id) ON DELETE RESTRICT,
 job_posting_id UUID NOT NULL REFERENCES job_posting(id) ON DELETE RESTRICT, current_revision_id UUID,
 status VARCHAR(30) NOT NULL CHECK(status IN ('REQUESTED','GENERATING','READY','FAILED','STALE','ARCHIVED')),
 created_by VARCHAR(150) NOT NULL, stale_reason VARCHAR(120), record_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(candidate_id,job_posting_id)
);

CREATE TABLE application_package_revision (
 id UUID PRIMARY KEY, application_package_id UUID NOT NULL REFERENCES application_package(id) ON DELETE RESTRICT,
 revision_number INTEGER NOT NULL CHECK(revision_number>0),
 candidate_profile_version_id UUID NOT NULL REFERENCES candidate_profile_version(id) ON DELETE RESTRICT,
 job_evaluation_id UUID NOT NULL REFERENCES job_evaluation(id) ON DELETE RESTRICT,
 source_job_checksum CHAR(64) NOT NULL, source_profile_checksum CHAR(64) NOT NULL,
 source_evaluation_checksum CHAR(64) NOT NULL, generation_prompt_version VARCHAR(40) NOT NULL,
 generation_schema_version VARCHAR(40) NOT NULL, template_version VARCHAR(40) NOT NULL,
 model VARCHAR(100) NOT NULL, generation_cache_key CHAR(64) NOT NULL,
 generation_warnings JSONB NOT NULL DEFAULT '[]', unsupported_requirements JSONB NOT NULL DEFAULT '[]',
 status VARCHAR(30) NOT NULL CHECK(status IN ('REQUESTED','PLANNING','GENERATING','VALIDATING','RENDERING','READY','FAILED','STALE')),
 failure_code VARCHAR(80), failure_message VARCHAR(500), created_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ,
 UNIQUE(application_package_id,revision_number), UNIQUE(generation_cache_key)
);
ALTER TABLE application_package ADD CONSTRAINT fk_application_package_current_revision
 FOREIGN KEY(current_revision_id) REFERENCES application_package_revision(id) ON DELETE RESTRICT;

CREATE TABLE generated_content (
 id UUID PRIMARY KEY, package_revision_id UUID NOT NULL REFERENCES application_package_revision(id) ON DELETE RESTRICT,
 content_type VARCHAR(40) NOT NULL CHECK(content_type IN ('RESUME_HEADLINE','PROFESSIONAL_SUMMARY','SKILL_SECTION','EXPERIENCE_BULLET','PROJECT_BULLET','EDUCATION_SECTION','COVER_LETTER','RECRUITER_MESSAGE','APPLICATION_ANSWER')),
 content_key VARCHAR(150) NOT NULL, section_order INTEGER NOT NULL CHECK(section_order>=0), text TEXT NOT NULL,
 origin VARCHAR(30) NOT NULL CHECK(origin IN ('DETERMINISTIC','AI_GENERATED','USER_EDITED')),
 verification_status VARCHAR(30) NOT NULL CHECK(verification_status IN ('VERIFIED','PARTIALLY_VERIFIED','UNVERIFIED','REJECTED')),
 user_edited BOOLEAN NOT NULL DEFAULT FALSE, record_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(package_revision_id,content_type,content_key)
);
CREATE INDEX idx_generated_content_revision_order ON generated_content(package_revision_id,section_order,id);

CREATE TABLE generated_claim (
 id UUID PRIMARY KEY, generated_content_id UUID NOT NULL REFERENCES generated_content(id) ON DELETE RESTRICT,
 claim_text VARCHAR(2000) NOT NULL, claim_type VARCHAR(40) NOT NULL CHECK(claim_type IN ('CANDIDATE_FACT','JOB_REFERENCE','NON_FACTUAL')),
 content_path VARCHAR(300) NOT NULL, validation_status VARCHAR(30) NOT NULL CHECK(validation_status IN ('VALID','INVALID','REQUIRES_REVIEW')),
 validation_codes JSONB NOT NULL DEFAULT '[]', created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_generated_claim_content ON generated_claim(generated_content_id,id);

CREATE TABLE generated_claim_source (
 id UUID PRIMARY KEY, generated_claim_id UUID NOT NULL REFERENCES generated_claim(id) ON DELETE RESTRICT,
 candidate_fact_id UUID REFERENCES resume_fact(id) ON DELETE RESTRICT,
 job_requirement_id UUID REFERENCES job_evaluation_requirement(id) ON DELETE RESTRICT,
 job_field_reference VARCHAR(100),
 CHECK(candidate_fact_id IS NOT NULL OR job_requirement_id IS NOT NULL OR job_field_reference IS NOT NULL),
 UNIQUE NULLS NOT DISTINCT(generated_claim_id,candidate_fact_id,job_requirement_id,job_field_reference)
);

CREATE TABLE application_question_draft (
 id UUID PRIMARY KEY, package_revision_id UUID NOT NULL REFERENCES application_package_revision(id) ON DELETE RESTRICT,
 question VARCHAR(2000) NOT NULL, question_hash CHAR(64) NOT NULL, normalized_question_key VARCHAR(500) NOT NULL,
 classification VARCHAR(40) NOT NULL CHECK(classification IN ('VERIFIED_AUTOMATIC','SUGGESTED_REQUIRES_REVIEW','USER_INPUT_REQUIRED','SENSITIVE_NEVER_AUTOMATIC')),
 draft_answer TEXT, answer_status VARCHAR(30) NOT NULL CHECK(answer_status IN ('DRAFTED','USER_INPUT_REQUIRED','BLOCKED_SENSITIVE')),
 confidence INTEGER CHECK(confidence BETWEEN 0 AND 100), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(package_revision_id,question_hash)
);

CREATE TABLE document_artifact (
 id UUID PRIMARY KEY, package_revision_id UUID NOT NULL REFERENCES application_package_revision(id) ON DELETE RESTRICT,
 artifact_type VARCHAR(30) NOT NULL CHECK(artifact_type IN ('HTML_PREVIEW','PDF_RESUME','DOCX_RESUME')),
 storage_bucket VARCHAR(100) NOT NULL, storage_key VARCHAR(700) NOT NULL UNIQUE, file_name VARCHAR(255) NOT NULL,
 content_type VARCHAR(150) NOT NULL, size_bytes BIGINT NOT NULL CHECK(size_bytes>0), sha256_checksum CHAR(64) NOT NULL,
 template_version VARCHAR(40) NOT NULL, rendering_status VARCHAR(30) NOT NULL CHECK(rendering_status IN ('READY','FAILED')),
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(package_revision_id,artifact_type)
);
CREATE INDEX idx_application_package_status_created ON application_package(status,created_at DESC);
CREATE INDEX idx_application_revision_package_created ON application_package_revision(application_package_id,created_at DESC);
CREATE INDEX idx_application_revision_sources ON application_package_revision(candidate_profile_version_id,job_evaluation_id);
CREATE INDEX idx_question_revision ON application_question_draft(package_revision_id,created_at,id);
CREATE INDEX idx_artifact_revision ON document_artifact(package_revision_id,artifact_type);
