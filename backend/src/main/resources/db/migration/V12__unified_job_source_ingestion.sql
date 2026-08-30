-- Expand the Phase 3 ingestion schema without requiring the Phase 3 entity
-- mappings to change in the same deployment step. Newly mapped columns that
-- cannot yet be supplied by legacy code remain nullable; the application
-- service owns their required combinations until a later contract migration
-- can make them physically NOT NULL.

DO $$
DECLARE constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'job_agent.job_source_configuration'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%source_type%'
    LOOP
        EXECUTE format('ALTER TABLE job_agent.job_source_configuration DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE job_agent.job_source_configuration
    ADD COLUMN connector_type VARCHAR(40),
    ADD COLUMN career_site_url VARCHAR(2000),
    ADD COLUMN canonical_host VARCHAR(253),
    ADD COLUMN support_status VARCHAR(40) NOT NULL DEFAULT 'SUPPORTED',
    ADD COLUMN support_message VARCHAR(500),
    ADD COLUMN webhook_token_hash CHAR(64),
    ADD COLUMN detection_version VARCHAR(80),
    ADD COLUMN extraction_recipe_version VARCHAR(80),
    ADD COLUMN last_connection_test_at TIMESTAMPTZ,
    ADD COLUMN last_connection_test_status VARCHAR(40);

UPDATE job_agent.job_source_configuration
SET connector_type = CASE source_type
    WHEN 'LEVER' THEN 'LEVER'
    WHEN 'GREENHOUSE' THEN 'GREENHOUSE'
    WHEN 'EMAIL_WEBHOOK' THEN 'EMAIL'
    ELSE connector_type
END
WHERE connector_type IS NULL;

ALTER TABLE job_agent.job_source_configuration
    ADD CONSTRAINT ck_job_source_configuration_source_type
        CHECK (source_type IN ('LEVER','GREENHOUSE','EMAIL_WEBHOOK','EXTERNAL_API','CAREER_SITE')),
    ADD CONSTRAINT ck_job_source_configuration_region
        CHECK ((source_type='LEVER' AND region IN ('GLOBAL','EU')) OR (source_type<>'LEVER' AND region='DEFAULT')),
    ADD CONSTRAINT ck_job_source_configuration_connector_type
        CHECK (connector_type IS NULL OR connector_type IN ('LEVER','GREENHOUSE','EMAIL','JSEARCH','CUSTOM_WEBHOOK','ORACLE_CX','WORKDAY','SMARTRECRUITERS','GENERIC_JSON_LD','CUSTOM_RECIPE')),
    ADD CONSTRAINT ck_job_source_configuration_support_status
        CHECK (support_status IN ('SUPPORTED','NEEDS_AUTHORIZATION','NEEDS_ADAPTER','NEEDS_EXTRACTION_RECIPE','UNSUPPORTED','VALIDATION_FAILED')),
    ADD CONSTRAINT ck_job_source_configuration_token_hash
        CHECK (webhook_token_hash IS NULL OR webhook_token_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_job_source_configuration_career_identity
        CHECK (source_type <> 'CAREER_SITE' OR (career_site_url IS NOT NULL AND canonical_host IS NOT NULL)),
    ADD CONSTRAINT ck_job_source_configuration_custom_recipe
        CHECK (connector_type <> 'CUSTOM_RECIPE' OR source_type = 'CAREER_SITE');

CREATE INDEX idx_job_source_connector_support
    ON job_agent.job_source_configuration(connector_type,support_status)
    WHERE archived_at IS NULL;
CREATE INDEX idx_job_source_canonical_host
    ON job_agent.job_source_configuration(LOWER(canonical_host))
    WHERE canonical_host IS NOT NULL AND archived_at IS NULL;

CREATE TABLE job_agent.job_source_search_rule (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES job_agent.job_source_configuration(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    query VARCHAR(500) NOT NULL,
    locations JSONB NOT NULL DEFAULT '[]'::jsonb,
    remote_allowed BOOLEAN NOT NULL,
    hybrid_allowed BOOLEAN NOT NULL,
    onsite_allowed BOOLEAN NOT NULL,
    date_posted_window VARCHAR(30) NOT NULL,
    maximum_results INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    record_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_job_source_search_rule_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT ck_job_source_search_rule_query CHECK (BTRIM(query) <> ''),
    CONSTRAINT ck_job_source_search_rule_locations CHECK (jsonb_typeof(locations) = 'array'),
    CONSTRAINT ck_job_source_search_rule_workplace CHECK (remote_allowed OR hybrid_allowed OR onsite_allowed),
    CONSTRAINT ck_job_source_search_rule_date_window CHECK (date_posted_window IN ('ANY','TODAY','THREE_DAYS','WEEK','MONTH')),
    CONSTRAINT ck_job_source_search_rule_maximum_results CHECK (maximum_results BETWEEN 1 AND 1000)
);
CREATE UNIQUE INDEX uq_job_source_search_rule_name
    ON job_agent.job_source_search_rule(source_id,LOWER(name));
CREATE INDEX idx_job_source_search_rule_enabled
    ON job_agent.job_source_search_rule(source_id,enabled,created_at,id);

CREATE TABLE job_agent.external_ingestion_event (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES job_agent.job_source_configuration(id) ON DELETE RESTRICT,
    event_id VARCHAR(300) NOT NULL,
    ingestion_provider VARCHAR(40) NOT NULL,
    search_rule_id UUID REFERENCES job_agent.job_source_search_rule(id) ON DELETE RESTRICT,
    discovery_query VARCHAR(500),
    fetched_at TIMESTAMPTZ NOT NULL,
    payload_checksum CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_external_ingestion_event_identity UNIQUE(source_id,event_id),
    CONSTRAINT ck_external_ingestion_event_id CHECK (event_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,299}$'),
    CONSTRAINT ck_external_ingestion_event_provider CHECK (ingestion_provider IN ('JSEARCH','CUSTOM_WEBHOOK','CUSTOM_RECIPE')),
    CONSTRAINT ck_external_ingestion_event_checksum CHECK (payload_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_external_ingestion_event_status CHECK (status IN ('PROCESSING','SUCCEEDED','PARTIAL_SUCCESS','FAILED')),
    CONSTRAINT ck_external_ingestion_event_counts CHECK (
        discovered_count >= 0 AND created_count >= 0 AND updated_count >= 0 AND
        unchanged_count >= 0 AND duplicate_count >= 0 AND failed_count >= 0
    )
);
CREATE INDEX idx_external_ingestion_event_source_created
    ON job_agent.external_ingestion_event(source_id,created_at DESC,id);
CREATE INDEX idx_external_ingestion_event_status_created
    ON job_agent.external_ingestion_event(status,created_at DESC,id);

DO $$
DECLARE constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'job_agent.job_source_run'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%trigger_type%'
    LOOP
        EXECUTE format('ALTER TABLE job_agent.job_source_run DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE job_agent.job_source_run
    ADD COLUMN coverage VARCHAR(30) NOT NULL DEFAULT 'COMPLETE_INVENTORY',
    ADD COLUMN search_rule_id UUID REFERENCES job_agent.job_source_search_rule(id) ON DELETE RESTRICT,
    ADD COLUMN external_event_id UUID REFERENCES job_agent.external_ingestion_event(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_job_source_run_trigger_type
        CHECK (trigger_type IN ('MANUAL','N8N','RETRY','WEBHOOK','EXTRACTION_WORKER')),
    ADD CONSTRAINT ck_job_source_run_coverage
        CHECK (coverage IN ('COMPLETE_INVENTORY','FILTERED_QUERY','PUSH_BATCH')),
    ADD CONSTRAINT uq_job_source_run_external_event UNIQUE(external_event_id);

CREATE INDEX idx_job_source_run_search_rule
    ON job_agent.job_source_run(search_rule_id,created_at DESC)
    WHERE search_rule_id IS NOT NULL;

DO $$
DECLARE constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'job_agent.job_posting'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%source_type%'
    LOOP
        EXECUTE format('ALTER TABLE job_agent.job_posting DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE job_agent.job_posting
    ADD COLUMN ingestion_provider VARCHAR(40),
    ADD COLUMN origin_publisher VARCHAR(80),
    ADD COLUMN discovery_query VARCHAR(500),
    ADD COLUMN external_event_id UUID REFERENCES job_agent.external_ingestion_event(id) ON DELETE RESTRICT,
    ADD COLUMN extraction_recipe_version VARCHAR(80);

UPDATE job_agent.job_posting
SET ingestion_provider = CASE source_type
    WHEN 'LEVER' THEN 'LEVER'
    WHEN 'GREENHOUSE' THEN 'GREENHOUSE'
    WHEN 'EMAIL_WEBHOOK' THEN 'EMAIL'
    WHEN 'MANUAL' THEN 'MANUAL'
    ELSE ingestion_provider
END
WHERE ingestion_provider IS NULL;

ALTER TABLE job_agent.job_posting
    ADD CONSTRAINT ck_job_posting_source_type
        CHECK (source_type IN ('LEVER','GREENHOUSE','EMAIL_WEBHOOK','EXTERNAL_API','CAREER_SITE','MANUAL')),
    ADD CONSTRAINT ck_job_posting_source_identity
        CHECK ((source_type='MANUAL' AND source_id IS NULL) OR (source_type<>'MANUAL' AND source_id IS NOT NULL)),
    ADD CONSTRAINT ck_job_posting_ingestion_provider
        CHECK (ingestion_provider IS NULL OR ingestion_provider IN ('LEVER','GREENHOUSE','EMAIL','MANUAL','JSEARCH','CUSTOM_WEBHOOK','ORACLE_CX','WORKDAY','SMARTRECRUITERS','GENERIC_JSON_LD','CUSTOM_RECIPE')),
    ADD CONSTRAINT ck_job_posting_origin_publisher
        CHECK (origin_publisher IS NULL OR BTRIM(origin_publisher) <> '');

CREATE INDEX idx_job_posting_ingestion_provider
    ON job_agent.job_posting(ingestion_provider,first_seen_at DESC,id);
CREATE INDEX idx_job_posting_origin_publisher
    ON job_agent.job_posting(LOWER(origin_publisher),first_seen_at DESC,id)
    WHERE origin_publisher IS NOT NULL;
CREATE INDEX idx_job_posting_external_event
    ON job_agent.job_posting(external_event_id,id)
    WHERE external_event_id IS NOT NULL;

CREATE TABLE job_agent.external_ingestion_event_result (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES job_agent.external_ingestion_event(id) ON DELETE RESTRICT,
    request_index INTEGER NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    job_id UUID REFERENCES job_agent.job_posting(id) ON DELETE RESTRICT,
    action VARCHAR(30),
    safe_error_code VARCHAR(80),
    safe_error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_external_ingestion_event_result_index UNIQUE(event_id,request_index),
    CONSTRAINT ck_external_ingestion_event_result_index CHECK (request_index BETWEEN 0 AND 99),
    CONSTRAINT ck_external_ingestion_event_result_action CHECK (action IS NULL OR action IN ('CREATED','UPDATED','UNCHANGED','DUPLICATE')),
    CONSTRAINT ck_external_ingestion_event_result_outcome CHECK (
        (action IS NOT NULL AND job_id IS NOT NULL AND safe_error_code IS NULL AND safe_error_message IS NULL) OR
        (action IS NULL AND job_id IS NULL AND safe_error_code IS NOT NULL)
    )
);
CREATE INDEX idx_external_ingestion_event_result_event
    ON job_agent.external_ingestion_event_result(event_id,request_index);
