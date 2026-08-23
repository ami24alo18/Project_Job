CREATE TABLE job_agent.application_package_idempotency_alias (
    idempotency_key_hash CHAR(64) PRIMARY KEY,
    application_package_revision_id UUID NOT NULL
        REFERENCES job_agent.application_package_revision(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_application_idempotency_alias_revision
    ON job_agent.application_package_idempotency_alias(application_package_revision_id);

-- Preserve aliases written to the original single-key column before aliases became
-- many-to-one. Raw Idempotency-Key values have never been persisted.
INSERT INTO job_agent.application_package_idempotency_alias(
    idempotency_key_hash,
    application_package_revision_id,
    created_at
)
SELECT idempotency_key_hash, id, created_at
FROM job_agent.application_package_revision
WHERE idempotency_key_hash IS NOT NULL
ON CONFLICT (idempotency_key_hash) DO NOTHING;
