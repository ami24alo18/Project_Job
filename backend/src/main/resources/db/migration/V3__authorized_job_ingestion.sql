CREATE TABLE job_source_configuration (
    id UUID PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    source_type VARCHAR(30) NOT NULL CHECK (source_type IN ('LEVER','GREENHOUSE','EMAIL_WEBHOOK')),
    provider_identifier VARCHAR(200) NOT NULL,
    region VARCHAR(20) NOT NULL CHECK (region IN ('DEFAULT','GLOBAL','EU')),
    enabled BOOLEAN NOT NULL,
    page_size INTEGER NOT NULL CHECK (page_size BETWEEN 1 AND 100),
    maximum_pages_per_run INTEGER NOT NULL CHECK (maximum_pages_per_run BETWEEN 1 AND 100),
    missing_run_threshold INTEGER NOT NULL CHECK (missing_run_threshold BETWEEN 1 AND 20),
    last_successful_sync_at TIMESTAMPTZ,
    last_attempted_sync_at TIMESTAMPTZ,
    consecutive_failure_count INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failure_count >= 0),
    record_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    CHECK ((source_type='LEVER' AND region IN ('GLOBAL','EU')) OR (source_type<>'LEVER' AND region='DEFAULT'))
);
CREATE UNIQUE INDEX uq_job_source_active_identity ON job_source_configuration(source_type,LOWER(provider_identifier),region) WHERE archived_at IS NULL;
CREATE INDEX idx_job_source_enabled_type ON job_source_configuration(enabled,source_type) WHERE archived_at IS NULL;

CREATE TABLE job_source_run (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES job_source_configuration(id) ON DELETE RESTRICT,
    trigger_type VARCHAR(20) NOT NULL CHECK (trigger_type IN ('MANUAL','N8N','RETRY')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED')),
    checkpoint TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    discovered_count INTEGER NOT NULL DEFAULT 0 CHECK (discovered_count >= 0),
    created_count INTEGER NOT NULL DEFAULT 0 CHECK (created_count >= 0),
    updated_count INTEGER NOT NULL DEFAULT 0 CHECK (updated_count >= 0),
    unchanged_count INTEGER NOT NULL DEFAULT 0 CHECK (unchanged_count >= 0),
    duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    removed_count INTEGER NOT NULL DEFAULT 0 CHECK (removed_count >= 0),
    safe_error_code VARCHAR(80),
    safe_error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_job_source_active_run ON job_source_run(source_id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX idx_job_source_run_source_created ON job_source_run(source_id,created_at DESC);
CREATE INDEX idx_job_source_run_status_created ON job_source_run(status,created_at DESC);

CREATE TABLE job_source_run_error (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES job_source_run(id) ON DELETE RESTRICT,
    external_id VARCHAR(300),
    safe_error_code VARCHAR(80) NOT NULL,
    safe_error_message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_job_source_run_error_run ON job_source_run_error(run_id,created_at,id);

CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    source_id UUID REFERENCES job_source_configuration(id) ON DELETE RESTRICT,
    source_type VARCHAR(30) NOT NULL CHECK (source_type IN ('LEVER','GREENHOUSE','EMAIL_WEBHOOK','MANUAL')),
    external_id VARCHAR(300) NOT NULL,
    duplicate_of_job_id UUID REFERENCES job_posting(id) ON DELETE RESTRICT,
    company VARCHAR(200) NOT NULL,
    title VARCHAR(300) NOT NULL,
    location VARCHAR(300),
    country_code CHAR(2),
    workplace_type VARCHAR(30) NOT NULL CHECK (workplace_type IN ('REMOTE','HYBRID','ONSITE','UNSPECIFIED')),
    employment_type VARCHAR(30) NOT NULL CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERNSHIP','OTHER','UNSPECIFIED')),
    department VARCHAR(200),
    team VARCHAR(200),
    description_plain_text TEXT,
    description_truncated BOOLEAN NOT NULL,
    apply_url VARCHAR(2000),
    canonical_apply_url VARCHAR(2000),
    source_url VARCHAR(2000),
    salary_minimum NUMERIC(19,4),
    salary_maximum NUMERIC(19,4),
    salary_currency CHAR(3),
    salary_interval VARCHAR(30) NOT NULL CHECK (salary_interval IN ('HOUR','DAY','WEEK','MONTH','YEAR','OTHER','UNSPECIFIED')),
    published_at TIMESTAMPTZ,
    source_updated_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    missing_successful_run_count INTEGER NOT NULL DEFAULT 0 CHECK (missing_successful_run_count >= 0),
    fingerprint CHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_content_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('READY_FOR_EVALUATION','NEEDS_REVIEW','DUPLICATE','EXPIRED','SOURCE_REMOVED','ARCHIVED')),
    status_before_archive VARCHAR(30) CHECK (status_before_archive IS NULL OR status_before_archive IN ('READY_FOR_EVALUATION','NEEDS_REVIEW','DUPLICATE','EXPIRED','SOURCE_REMOVED')),
    manually_edited BOOLEAN NOT NULL,
    source_update_available BOOLEAN NOT NULL,
    last_seen_run_id UUID REFERENCES job_source_run(id) ON DELETE RESTRICT,
    manual_idempotency_key VARCHAR(200),
    record_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(source_id,external_id),
    CHECK ((source_type='MANUAL' AND source_id IS NULL) OR (source_type<>'MANUAL' AND source_id IS NOT NULL)),
    CHECK (salary_minimum IS NULL OR salary_minimum >= 0),
    CHECK (salary_maximum IS NULL OR salary_maximum >= 0),
    CHECK (salary_minimum IS NULL OR salary_maximum IS NULL OR salary_maximum >= salary_minimum),
    CHECK ((status='DUPLICATE' AND duplicate_of_job_id IS NOT NULL) OR status<>'DUPLICATE')
);
CREATE UNIQUE INDEX uq_job_posting_manual_idempotency ON job_posting(manual_idempotency_key) WHERE manual_idempotency_key IS NOT NULL;
CREATE INDEX idx_job_posting_source_status ON job_posting(source_id,status,last_seen_at DESC);
CREATE INDEX idx_job_posting_status_first_seen ON job_posting(status,first_seen_at DESC,id);
CREATE INDEX idx_job_posting_company_lower ON job_posting(LOWER(company));
CREATE INDEX idx_job_posting_title_lower ON job_posting(LOWER(title));
CREATE INDEX idx_job_posting_location_lower ON job_posting(LOWER(location));
CREATE INDEX idx_job_posting_published_at ON job_posting(published_at DESC);
CREATE INDEX idx_job_posting_canonical_url ON job_posting(canonical_apply_url) WHERE canonical_apply_url IS NOT NULL;
CREATE INDEX idx_job_posting_fingerprint ON job_posting(fingerprint);
CREATE INDEX idx_job_posting_content_hash ON job_posting(content_hash);
CREATE INDEX idx_job_posting_source_content_hash ON job_posting(source_content_hash);
CREATE INDEX idx_job_posting_duplicate_of ON job_posting(duplicate_of_job_id) WHERE duplicate_of_job_id IS NOT NULL;

CREATE TABLE email_ingestion_event (
    id UUID PRIMARY KEY,
    message_id VARCHAR(300) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL CHECK (provider IN ('GMAIL','OTHER')),
    source_id UUID NOT NULL REFERENCES job_source_configuration(id) ON DELETE RESTRICT,
    received_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PROCESSING','SUCCEEDED','PARTIAL_SUCCESS')),
    job_count INTEGER NOT NULL DEFAULT 0 CHECK (job_count >= 0),
    created_count INTEGER NOT NULL DEFAULT 0 CHECK (created_count >= 0),
    updated_count INTEGER NOT NULL DEFAULT 0 CHECK (updated_count >= 0),
    unchanged_count INTEGER NOT NULL DEFAULT 0 CHECK (unchanged_count >= 0),
    duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_ingestion_source_created ON email_ingestion_event(source_id,created_at DESC);
