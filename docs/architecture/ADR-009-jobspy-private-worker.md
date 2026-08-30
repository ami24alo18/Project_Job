# ADR-009: Add JobSpy through a private, policy-gated worker

- Status: Accepted
- Date: 2026-08-29
- Extends: [ADR-008](ADR-008-unified-career-site-and-external-job-ingestion.md)

## Context

JSearch quotas can interrupt job discovery, while the application must retain the existing n8n implementation and its provider-neutral ingestion contract. JobSpy supports multiple job boards but is Python software, and its MIT license is separate from every publisher's terms and access policy.

## Decision

Add `JOBSPY` as a distinct external connector and ingestion provider. A pinned Python worker owns the JobSpy dependency; n8n owns scheduling and orchestration; Spring accepts only the existing source-authenticated external-event schema. JSearch remains unchanged and independently selectable.

The worker is private to the Compose network, stateless, non-root, read-only, resource bounded, and authenticated with a deployment secret. Its request schema accepts search fields and deployment-allowlisted site identifiers, never arbitrary URLs, headers, credentials, cookies, proxies, or executable configuration. No site is allowlisted by default. Requests have bounded bodies, sites, result counts, concurrency, and a process-level hard timeout.

Every returned job has a stable external identity, `ingestionProvider=JOBSPY`, and an origin publisher. Spring still owns normalization, exact deduplication, replay semantics, durable run history, and candidate matching. JobSpy batches use `PUSH_BATCH`; they cannot advance missing-job counters.

The deployment policy forbids using the worker to bypass authentication, CAPTCHAs, blocking, or rate limits. A board must be disabled when authorization is absent or access is rejected. Direct company-career-site discovery remains existing functionality but is not expanded by this decision.

## Consequences

- JobSpy can be enabled without consuming JSearch quota, while either workflow can be disabled independently.
- Upgrading JobSpy is an explicit dependency and compatibility review, not an unpinned image rebuild.
- Board availability can change independently of the worker; empty or failed runs are expected operational outcomes, not permission to evade controls.
- n8n needs two separate secrets: the worker token and the Job Agent source token.
- A future provider can reuse the same external-event boundary without changing the job domain.
