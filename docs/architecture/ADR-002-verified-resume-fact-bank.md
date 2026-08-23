# ADR-002: Verified resume fact bank and immutable candidate snapshots

- Status: Accepted
- Date: 2026-08-22

## Context

Later phases may generate job-specific text. A resume or free-form profile can contain ambiguity, stale claims, formatting artifacts, and extractor mistakes. Treating such input as automatically true would allow generated applications to invent or distort a candidate's history.

The system also needs reproducibility: a human must be able to see exactly which approved data a later decision used, even after the working profile changes.

## Decision

Candidate data remains inside the modular monolith but is split into explicit aggregates: core profile, search preferences, resume facts, reusable answers, resume-document metadata, and published versions.

Resume facts use `DRAFT`, `VERIFIED`, `REJECTED`, and `ARCHIVED` states. Creation and structured import always produce `DRAFT`; only an authenticated, audited action may verify a fact. Rejection and archival remove facts from eligible data without destructive deletion. Editing evidence-bearing fields or tags on a verified fact resets it to `DRAFT` and clears its verifier metadata because the prior human decision no longer covers the changed claim.

Reusable answers use a similar lifecycle and add sensitivity policy. Sensitive categories cannot be labelled safe for automatic entry. Future consumers may use only verified, non-archived facts and answers and must respect answer sensitivity.

Publication builds deterministic canonical JSON from the current profile and preferences, verified facts and answers, and active master-resume metadata. It stores a SHA-256 checksum and creates a monotonically numbered version. Published content and provenance are immutable; only the internal active-version marker changes when a successor is published. Matching content reuses the latest version rather than creating noise.

Resume binaries are private MinIO objects. PostgreSQL holds identity, checksum, state, storage key, and extracted plain text. This separation keeps relational queries and version snapshots small, prevents binary database growth, and permits an object-storage implementation behind a narrow boundary. Storage keys and credentials are never returned to the browser.

Apache Tika extracts bounded plain text after upload. Extraction failure is recorded while the original binary remains durable. Extracted text is reference material only: it is never transformed into facts automatically in Phase 2.

## Consequences

- Later AI and matching work has a stable rule: consume an active published version, never mutable draft tables or raw extracted text.
- Verification transitions and publications create safe audit events without storing full resumes, answers, credentials, or request bodies.
- Optimistic locking protects mutable aggregates; important records are archived rather than deleted.
- PostgreSQL and MinIO backups must be coordinated because document metadata and binaries live in different durable stores.
- Publishing requires explicit readiness data and can return field-specific `422` errors.
- Single-user Basic authentication is acceptable only as a temporary self-hosted boundary and requires HTTPS outside local development.

## Alternatives considered

Storing one editable resume JSON document was rejected because it obscures per-claim review and makes lifecycle queries difficult. Storing binaries in PostgreSQL was rejected because MinIO already supplies private durable object storage. Automatic LLM extraction was deferred because it would add an untrusted interpretation step before the verification workflow, broaden credentials and failure modes, and is explicitly Phase 3 work.
