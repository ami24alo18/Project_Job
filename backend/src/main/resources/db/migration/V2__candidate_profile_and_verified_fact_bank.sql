CREATE TABLE candidate_profile (
    id UUID PRIMARY KEY,
    singleton_key BOOLEAN NOT NULL DEFAULT TRUE UNIQUE CHECK (singleton_key),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(40),
    professional_title VARCHAR(150) NOT NULL,
    professional_summary VARCHAR(4000),
    current_company VARCHAR(200),
    current_location VARCHAR(200),
    total_experience_months INTEGER NOT NULL CHECK (total_experience_months >= 0),
    notice_period_days INTEGER NOT NULL CHECK (notice_period_days >= 0),
    serving_notice_period BOOLEAN NOT NULL,
    linkedin_url VARCHAR(500), github_url VARCHAR(500), leetcode_url VARCHAR(500), portfolio_url VARCHAR(500),
    record_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE search_preference (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL UNIQUE REFERENCES candidate_profile(id) ON DELETE RESTRICT,
    minimum_experience_years INTEGER NOT NULL CHECK (minimum_experience_years >= 0),
    maximum_experience_years INTEGER NOT NULL CHECK (maximum_experience_years >= minimum_experience_years),
    minimum_match_score INTEGER NOT NULL CHECK (minimum_match_score BETWEEN 0 AND 100),
    maximum_daily_shortlist INTEGER NOT NULL CHECK (maximum_daily_shortlist BETWEEN 1 AND 500),
    maximum_daily_applications INTEGER NOT NULL CHECK (maximum_daily_applications BETWEEN 1 AND 100),
    remote_allowed BOOLEAN NOT NULL, hybrid_allowed BOOLEAN NOT NULL, onsite_allowed BOOLEAN NOT NULL,
    record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE search_preference_target_title (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(200) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_preferred_location (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(200) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_excluded_location (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(200) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_required_skill (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(150) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_preferred_skill (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(150) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_excluded_company (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(200) NOT NULL, PRIMARY KEY(preference_id,preference_value));
CREATE TABLE search_preference_excluded_keyword (preference_id UUID NOT NULL REFERENCES search_preference(id) ON DELETE CASCADE, preference_value VARCHAR(200) NOT NULL, PRIMARY KEY(preference_id,preference_value));

CREATE TABLE resume_document (
    id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES candidate_profile(id) ON DELETE RESTRICT,
    document_type VARCHAR(30) NOT NULL CHECK (document_type IN ('MASTER_RESUME')),
    original_file_name VARCHAR(255) NOT NULL, sanitized_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL, size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256_checksum CHAR(64) NOT NULL, storage_key VARCHAR(700) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('UPLOADED','TEXT_EXTRACTED','EXTRACTION_FAILED','ARCHIVED')),
    active BOOLEAN NOT NULL DEFAULT FALSE, extracted_text TEXT, extraction_error VARCHAR(500),
    record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_resume_document_active_master ON resume_document(profile_id) WHERE active = TRUE AND document_type = 'MASTER_RESUME';
CREATE UNIQUE INDEX uq_resume_document_active_checksum ON resume_document(profile_id,sha256_checksum) WHERE status <> 'ARCHIVED';

CREATE TABLE resume_fact (
    id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES candidate_profile(id) ON DELETE RESTRICT,
    category VARCHAR(30) NOT NULL, statement VARCHAR(4000) NOT NULL, company VARCHAR(200), start_date DATE, end_date DATE,
    status VARCHAR(30) NOT NULL, source_type VARCHAR(30) NOT NULL, source_document_id UUID REFERENCES resume_document(id) ON DELETE RESTRICT,
    source_reference VARCHAR(500), evidence_text VARCHAR(4000), verified_at TIMESTAMPTZ, verified_by VARCHAR(150),
    record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CHECK (status IN ('DRAFT','VERIFIED','REJECTED','ARCHIVED')),
    CHECK (category IN ('EMPLOYMENT','PROJECT','ACHIEVEMENT','EDUCATION','SKILL','CERTIFICATION','OTHER')),
    CHECK (source_type IN ('MANUAL','STRUCTURED_IMPORT','RESUME_DOCUMENT')),
    CHECK ((status='VERIFIED' AND verified_at IS NOT NULL AND verified_by IS NOT NULL) OR (status<>'VERIFIED' AND verified_at IS NULL AND verified_by IS NULL))
);
CREATE INDEX idx_resume_fact_profile_status_category ON resume_fact(profile_id,status,category,id);
CREATE INDEX idx_resume_fact_statement_lower ON resume_fact(LOWER(statement));
CREATE TABLE resume_fact_skill_tag (fact_id UUID NOT NULL REFERENCES resume_fact(id) ON DELETE CASCADE, tag_value VARCHAR(150) NOT NULL, PRIMARY KEY(fact_id,tag_value));
CREATE TABLE resume_fact_domain_tag (fact_id UUID NOT NULL REFERENCES resume_fact(id) ON DELETE CASCADE, tag_value VARCHAR(150) NOT NULL, PRIMARY KEY(fact_id,tag_value));
CREATE INDEX idx_resume_fact_skill_tag_value ON resume_fact_skill_tag(LOWER(tag_value));
CREATE INDEX idx_resume_fact_domain_tag_value ON resume_fact_domain_tag(LOWER(tag_value));

CREATE TABLE reusable_answer (
    id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES candidate_profile(id) ON DELETE RESTRICT,
    question VARCHAR(1000) NOT NULL, normalized_question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL, category VARCHAR(30) NOT NULL, sensitivity VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL,
    verified_at TIMESTAMPTZ, record_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(profile_id,normalized_question),
    CHECK (category IN ('GENERAL','EXPERIENCE','NOTICE_PERIOD','COMPENSATION','WORK_AUTHORIZATION','RELOCATION','DEMOGRAPHIC','LEGAL','OTHER')),
    CHECK (sensitivity IN ('SAFE_AUTOFILL','REQUIRES_REVIEW','NEVER_AUTOFILL')),
    CHECK (status IN ('DRAFT','VERIFIED','ARCHIVED'))
);
CREATE INDEX idx_reusable_answer_filters ON reusable_answer(profile_id,status,category,sensitivity);

CREATE TABLE candidate_profile_version (
    id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES candidate_profile(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL CHECK (version_number > 0), snapshot_json JSONB NOT NULL,
    checksum CHAR(64) NOT NULL, change_reason VARCHAR(500) NOT NULL, active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, UNIQUE(profile_id,version_number)
);
CREATE UNIQUE INDEX uq_candidate_profile_version_active ON candidate_profile_version(profile_id) WHERE active = TRUE;
CREATE INDEX idx_candidate_profile_version_checksum ON candidate_profile_version(profile_id,checksum);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY, event_type VARCHAR(80) NOT NULL, aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL, actor VARCHAR(150) NOT NULL, timestamp TIMESTAMPTZ NOT NULL,
    safe_metadata TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_audit_event_aggregate ON audit_event(aggregate_type,aggregate_id,timestamp);
