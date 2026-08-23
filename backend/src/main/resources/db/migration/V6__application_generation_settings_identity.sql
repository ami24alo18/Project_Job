ALTER TABLE job_agent.application_package_revision
    ADD COLUMN generation_settings_checksum CHAR(64),
    ADD COLUMN idempotency_key_hash CHAR(64) UNIQUE;

-- Historical revisions predate an independently tracked settings identity. Their immutable
-- cache key is retained as a non-matching sentinel so they are surfaced as stale rather than
-- silently rebound to the current provider configuration.
UPDATE job_agent.application_package_revision
SET generation_settings_checksum = generation_cache_key
WHERE generation_settings_checksum IS NULL;

ALTER TABLE job_agent.application_package_revision
    ALTER COLUMN generation_settings_checksum SET NOT NULL;

ALTER TABLE job_agent.llm_execution
    ADD COLUMN source_job_checksum CHAR(64),
    ADD COLUMN source_profile_checksum CHAR(64),
    ADD COLUMN source_evaluation_checksum CHAR(64);
