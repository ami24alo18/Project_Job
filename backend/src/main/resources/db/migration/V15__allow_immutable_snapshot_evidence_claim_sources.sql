-- Candidate evidence can now be extracted directly into an immutable profile snapshot.
-- Keep the legacy resume_fact foreign key intact and store snapshot-only evidence in a
-- dedicated column. The application validates those UUIDs against the exact snapshot
-- attached to the package revision before persistence.
ALTER TABLE generated_claim_source
    ADD COLUMN candidate_snapshot_evidence_id UUID;

ALTER TABLE generated_claim_source
    DROP CONSTRAINT generated_claim_source_check,
    DROP CONSTRAINT generated_claim_source_generated_claim_id_candidate_fact_id_key;

ALTER TABLE generated_claim_source
    ADD CONSTRAINT generated_claim_source_has_evidence_check CHECK (
        candidate_fact_id IS NOT NULL
        OR candidate_snapshot_evidence_id IS NOT NULL
        OR job_requirement_id IS NOT NULL
        OR job_field_reference IS NOT NULL
    ),
    ADD CONSTRAINT generated_claim_source_evidence_key UNIQUE NULLS NOT DISTINCT (
        generated_claim_id,
        candidate_fact_id,
        candidate_snapshot_evidence_id,
        job_requirement_id,
        job_field_reference
    );

COMMENT ON COLUMN generated_claim_source.candidate_snapshot_evidence_id IS
    'Stable evidence UUID from the immutable candidate profile snapshot; validated by the application before persistence.';
