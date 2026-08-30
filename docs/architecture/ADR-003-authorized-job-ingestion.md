# ADR-003: Authorized public job ingestion and deterministic deduplication

- Status: Accepted
- Date: 2026-08-22

> **Extension:** [ADR-008](ADR-008-unified-career-site-and-external-job-ingestion.md) preserves these controls for built-in public feeds and adds guarded career-site discovery, source-scoped external batches, and a separately isolated allowlisted extraction fallback. It supersedes only the blanket rejection of every employer-page extraction path; arbitrary fetching and scraping inside the Spring application remain rejected.

## Context

Phase 3 brings untrusted job data into the self-hosted application from manual entry, public applicant-tracking-system feeds, and structured n8n events. Remote ingestion introduces several risks that are absent from local profile editing: server-side request forgery, unexpectedly large or malformed responses, executable markup, transient provider failures, duplicate postings, and incorrect removal decisions after an incomplete synchronization.

The system also needs a repeatable history. An operator must be able to distinguish newly discovered, updated, unchanged, duplicate, malformed, and source-removed jobs and relate those outcomes to a particular source run without retaining raw provider payloads.

## Decision

### Use only authorized public feeds

Provider base URLs are selected by backend code, never supplied by a user:

| Provider region | Allowed public API base |
| --- | --- |
| Lever global | `https://api.lever.co/v0/postings/` |
| Lever EU | `https://api.eu.lever.co/v0/postings/` |
| Greenhouse | `https://boards-api.greenhouse.io/v1/boards/` |

The Lever site identifier and Greenhouse board token are encoded as single path segments beneath those bases. A source configuration cannot contain a URL, credentials, headers, or executable request data. Redirects must not escape the provider allowlist. DNS names, IP addresses, unsupported schemes, local-network targets, `file:`, and `ftp:` cannot be selected through source configuration.

The connectors use only documented public GET endpoints. They do not scrape career pages, discover tenant identifiers, use employer-authenticated APIs, or call application-submission endpoints. URLs found inside job records are normalized as data for a user-initiated browser link and are never fetched by the backend.

Lever documents global and EU Postings API hosts, offset pagination through `skip` and `limit`, and a stable posting `id`. Greenhouse documents a public, unpaged jobs listing at `/v1/boards/{board_token}/jobs`; `content=true` includes job content, departments, and offices, and the posting `id` identifies the public job post. Greenhouse page-size and Harvest pagination rules are not applied to this separate Job Board API.

### Separate fetching, normalization, and persistence

The `jobsource` module owns provider configurations, connector adapters, checkpoints, run orchestration, HTTP resilience, and email-ingestion events. It emits provider-neutral raw records to the `job` module. The `job` module owns normalization, URL canonicalization, hashing, deterministic duplicate decisions, job lifecycle, and persistence. Both modules emit concise events through `audit`.

The dependency direction is:

```text
jobsource -> job
jobsource -> audit
job       -> audit
```

The `job` module does not depend on Lever or Greenhouse DTOs. This keeps provider schema changes at adapter boundaries and allows normalization and deduplication to be tested without remote services.

Remote HTTP calls occur outside database transactions. A connector first obtains a bounded response and maps it to raw records; short database transactions then persist the run and individual job outcomes. This avoids holding connections or locks across network waits and makes retry behavior independent from transaction rollback.

### Treat descriptions and provider fields as untrusted

Provider HTML is parsed with an established HTML parser and converted to bounded plain text. Scripts, styles, markup, unsafe control characters, and excess content are removed before persistence. The frontend renders only the normalized plain-text field. Complete descriptions and raw provider responses are excluded from logs and audit metadata.

Structured fields are mapped conservatively. Missing or syntactically invalid optional workplace, employment, country, salary, or timestamp values remain unknown rather than being inferred from prose. Invalid required identity, URL, and salary-range values fail only the affected record with a safe diagnostic. In particular, Lever's official Postings API field table does not provide publication, update, or expiry timestamps, so the connector leaves them unknown. Greenhouse `updated_at` represents a source update; it is not relabelled as a publication time. When a public provider response omits company, the source display name supplies the normalized company fallback.

### Prefer exact deterministic identity over fuzzy matching

Deduplication has three ordered levels:

1. `(sourceId, externalId)` is the provider identity. A repeated identity updates `lastSeenAt`; changed provider content updates the existing job and unchanged content records `UNCHANGED`.
2. A new record with the same canonical application URL as an active job is retained as `DUPLICATE` and points to `duplicateOfJobId`.
3. A SHA-256 fingerprint over normalized company, title, location, and canonical application URL host/path identifies an exact cross-source duplicate.

A separate content hash detects provider changes. Tracking parameters may be removed only from a small known set; parameters that could identify a job are preserved. Similar titles, descriptions, or employers alone do not trigger a duplicate decision. No fuzzy matching, embeddings, or AI are used.

Provider refreshes do not overwrite manual corrections. If provider content changes after a correction, the job retains the manual values and records that a source update is available for review.

### Make synchronization bounded and auditable

Each attempt has a durable run with trigger, status, checkpoint, timestamps, safe diagnostics, and outcome counts. A bounded Spring task executor runs synchronization and rejects excess work explicitly. Run/source diagnostic context crosses the executor boundary and safe worker logs contain lifecycle and aggregate metrics only. Only one active run is allowed per source, and stale `QUEUED` or `RUNNING` records are reconciled after a restart. A per-source overlap is a conflict; the bulk n8n endpoint instead returns the current run identifier/status for that source.

Safe GET requests may use bounded exponential backoff with jitter for timeouts, `5xx`, and `429`. A valid, bounded `Retry-After` value takes precedence. Neither provider publishes a listing-GET quota in its public documentation, so application-POST or Greenhouse Harvest limits are not reused. Permanent `4xx` responses are not retried except `429`.

Missing-job counts advance only after a demonstrably complete successful feed. Page-limit exhaustion, response-size exhaustion, malformed top-level payloads, failed requests, and partially processed feeds cannot mark jobs removed. A job becomes `SOURCE_REMOVED` only after the configured number of complete successful runs omit it. Trusted structured expiry dates may produce `EXPIRED`; age alone does not.

### Let n8n own scheduling and mailbox access

Spring does not schedule source synchronization. n8n calls the authenticated `sync-enabled` endpoint, polls returned run identifiers with a finite attempt count, and reports only failed or partial outcomes. This keeps operational scheduling visible and configurable without adding another scheduler to the application.

Mailbox credentials remain in n8n. The backend accepts only an approved structured email-alert event protected by the existing environment-backed n8n secret. `messageId` provides idempotency, and replay returns the stored acknowledgement rather than a conflict. Raw email bodies and unrelated message content are not accepted or stored by the backend. Because n8n may retain node input/output in execution history, the workflow projects to approved fields immediately and its execution-data saving/pruning policy treats the temporary mailbox payload as sensitive.

The repository provides workflow construction guides rather than claiming an unvalidated exported JSON file is importable.

## Consequences

- Adding another remote provider requires an explicit connector, normalizer mapping, fixed host allowlist, fixtures, and contract tests.
- A source run may be partial even when it persisted valid jobs; conservative completeness prevents false removals.
- Greenhouse returns its jobs feed without documented pagination, so response-byte limits rather than an invented page parameter bound that request.
- Exact duplicate rules can leave uncertain near-duplicates for human review. This is safer than silently merging unrelated roles.
- Provider payload changes are isolated at DTO boundaries, but documented and fixture-based compatibility tests must be maintained.
- PostgreSQL preserves job and run history; archival and source removal are state transitions, not deletion.

## Alternatives considered

Scraping employer pages, LinkedIn, or Indeed was rejected because it is brittle, broadens authorization and compliance risk, and defeats a provider-host allowlist. Accepting arbitrary feed URLs was rejected because it creates an SSRF primitive. Performing remote calls inside transactions was rejected because network latency would hold database resources and complicate retries. Fuzzy title or semantic matching was rejected because Phase 3 cannot safely establish identity from similarity. Spring scheduling was rejected because n8n already owns visible self-hosted orchestration.

## Official provider references

- [Lever Postings API](https://github.com/lever/postings-api/blob/master/README.md)
- [Greenhouse Job Board API](https://developer.greenhouse.io/job-board.html)
- [Greenhouse Job Board jobs documentation source](https://github.com/grnhse/greenhouse-api-docs/blob/master/source/includes/job-board/_jobs.md)
- [Greenhouse job-board token guidance](https://support.greenhouse.io/hc/en-us/articles/5888210160155-Find-your-job-board-URL)
- [HTTP 429 semantics](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
- [HTTP `Retry-After` syntax](https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after)
