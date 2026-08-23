# ADR-006: Human approval bound to immutable application revisions

- Status: Accepted
- Date: 2026-08-23

## Context

Phase 5 produces validated drafts and private artifacts, but `READY` is a generation state, not permission for an external action. A later edit, source change, validation result, or replaced artifact can make a previously reviewed draft materially different.

## Decision

Approval is an explicit, immutable decision about one application-package revision. The decision snapshots the complete review checklist, question resolutions, actor and role, exact job/profile/evaluation checksums, artifact-manifest checksum, and validation checksum. Review state is separate from generation state.

The server permits `APPROVED_FOR_HANDOFF` only after it independently checks successful generation/validation, current non-stale sources, candidate-claim provenance, sensitive-question policy, required-question resolutions, all checklist confirmations, and readable checksum-valid HTML/PDF/DOCX artifacts. Comments are mandatory for `CHANGES_REQUESTED` and `REJECTED`.

Decision rows are append-only. Invalidation timestamps and reasons annotate an approval without deleting it. A changed revision, edit, stale source, missing/corrupt artifact, or changed validation snapshot makes the approval unusable. The reusable `HandoffEligibilityService` rechecks these invariants; frontend state is never authoritative.

## Consequences

- Audit history explains who reviewed what exact bytes and evidence.
- Regeneration naturally requires a new approval because it creates a new revision.
- Phase 7 has one policy entry point and cannot infer permission from package generation status.
- Checking artifact integrity requires private object-storage reads, trading some latency for a fail-closed handoff boundary.

## Alternatives rejected

- Approval on the mutable package: ambiguous after regeneration.
- Approval based only on a status flag: cannot detect artifact or validation drift.
- Frontend-only checklist enforcement: bypassable and unsuitable for Phase 7.

