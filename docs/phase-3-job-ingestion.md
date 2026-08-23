# Phase 3 - Job ingestion and normalization

## Implemented scope

Phase 3 extends the modular monolith with the authorized ingestion workflow:

```text
Configure source -> queue synchronization -> fetch an authorized public feed
                 -> normalize untrusted records -> detect exact duplicates/updates
                 -> persist jobs and run metrics -> review normalized jobs
```

It supports manual jobs, Lever public Postings feeds, Greenhouse public Job Board feeds, and structured job-alert events delivered by n8n. It preserves source/run history, distinguishes new, updated, unchanged, duplicate, and failed records, and advances source-removal state only after complete successful synchronizations.

Phase 3 does not scrape websites, connect the backend to Gmail, submit applications, evaluate candidates, score or match jobs, infer skills or experience, call an LLM, tailor a resume, or generate application material.

## Architecture and module boundaries

Phase 3 follows [ADR-003](architecture/ADR-003-authorized-job-ingestion.md):

```text
jobsource -> job
jobsource -> audit
job       -> audit
```

- `jobsource` owns source configuration, public-feed connectors, provider DTOs, checkpoints, source-run orchestration, bounded asynchronous execution, safe HTTP retries, and structured email-ingestion events.
- `job` owns the provider-neutral job aggregate, normalization, description-to-plain-text conversion, URL canonicalization, content hashing, fingerprinting, exact duplicate decisions, manual entry, lifecycle transitions, querying, and summary counts.
- `audit` receives concise lifecycle events without descriptions, raw provider responses, secrets, headers, or stack traces.
- Controllers handle authentication and HTTP DTOs only. Application services coordinate use cases, and short transactions cover database mutations rather than remote calls.

UUIDs identify application records. Mutable aggregates use `Instant` UTC timestamps and optimistic `@Version` columns.

## Supported sources

| Source type | Provider identifier | Region behavior | Remote behavior |
| --- | --- | --- | --- |
| `MANUAL` | Generated internal identifier | Not applicable | No URL is fetched |
| `LEVER` | Lever site identifier | Global/default or EU | Public Postings API GET only |
| `GREENHOUSE` | Greenhouse board token | One canonical/default region | Public Job Board API GET only |
| `EMAIL_WEBHOOK` | Logical alert-source name | Default | n8n sends approved structured fields |

Remote base URLs are not configurable. The backend resolves them from this allowlist:

```text
https://api.lever.co/v0/postings/
https://api.eu.lever.co/v0/postings/
https://boards-api.greenhouse.io/v1/boards/
```

Provider identifiers are encoded as path segments. Application and source links inside a posting are retained only as validated `http`/`https` data and are never fetched by the backend.

### Lever behavior

The connector requests JSON from the documented `/v0/postings/{site}` endpoint and uses `skip` and `limit` for offset pagination. The configured page size and maximum pages bound a run. An empty or short page completes the feed; reaching the maximum page count on a full page is incomplete and cannot trigger missing-job accounting.

The provider posting `id` becomes `externalId`. Structured title, location, country, team, department, commitment, workplace type, salary, hosted URL, and application URL are mapped when present. The documented plain description is preferred when it is complete; documented `lists` and `additional` content are combined with the description when present, then all HTML passes through the common plain-text sanitizer before storage. Lever does not document publication, update, or expiry timestamps in its public field table, so the connector leaves those timestamps unknown. Lever does not supply a company field; the configured source display name is the normalized company fallback.

### Greenhouse behavior

The connector requests the documented `/v1/boards/{board_token}/jobs?content=true` endpoint. This jobs endpoint returns the complete board and does not document page, offset, or limit parameters. It is therefore treated as one provider page and is bounded by the HTTP response-size limit rather than invented pagination.

The public job-post `id` becomes `externalId`; `internal_job_id` is not used as provider identity. Title, `location.name`, `updated_at`, `absolute_url`, content, departments, and offices are mapped conservatively. Greenhouse content is entity-encoded HTML and may include board-level introduction and conclusion text, so one provider encoding layer is decoded before the markup is parsed to bounded plain text. When the response omits `company_name`, the configured source display name is the normalized company fallback. Optional or employer-specific metadata does not become a normalized field unless its meaning is explicit.

The per-job Greenhouse response documents `first_published` and `application_deadline`. If those fields are not supplied by the fetched representation, publication and expiry remain unknown; `updated_at` is never treated as publication time.

### Provider HTTP resilience

Connector clients configure connection and response timeouts, maximum response bytes, a finite redirect policy, and an identifying user agent. Only safe GET operations are eligible for retry. Timeouts, retryable `5xx`, and `429` use bounded exponential backoff with jitter. A valid `Retry-After` header is honored within the configured maximum delay. Validation failures and permanent `4xx` responses other than `429` are not retried.

Neither public provider documentation specifies a listing-GET quota. Lever's application-POST limit and Greenhouse Harvest limits are unrelated and are not applied to these connectors. Contract tests use local HTTP fixtures; build success does not depend on live provider availability.

## Domain model

### Job source configuration

A persistent source includes its display name, source type, provider identifier, canonical region, enabled/archive state, page/run bounds, missing-run threshold, last attempt and success timestamps, consecutive failure count, optimistic version, and audit timestamps.

Only valid source/region combinations are accepted. Duplicate active configurations for the same type, identifier, and canonical region are rejected. Disable and archive are state changes; no source is physically deleted. Archived sources cannot synchronize.

### Normalized job posting

`JobPosting` stores provider identity and provenance separately from normalized content. Its principal fields include:

- source identity: source ID, source type, external ID, duplicate reference;
- normalized content: company, title, location, country, workplace/employment type, department, team, bounded plain-text description, and truncation marker;
- destinations and structured values: canonical apply/source URLs, salary fields, publication/update/expiry timestamps;
- ingestion state: first/last seen times, missing-success count, deterministic fingerprint, provider content hash, status, manual-edit marker, and source-update availability;
- persistence state: optimistic record version and audit timestamps.

Statuses are `READY_FOR_EVALUATION`, `NEEDS_REVIEW`, `DUPLICATE`, `EXPIRED`, `SOURCE_REMOVED`, and `ARCHIVED`. They indicate ingestion readiness and retention only; no Phase 4 score or profile-match state exists.

### Source runs and email events

Each queued synchronization creates a `JobSourceRun` before worker execution. It records source, trigger (`MANUAL`, `N8N`, or `RETRY`), lifecycle (`QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIAL_SUCCESS`, or `FAILED`), checkpoint, timestamps, outcome counters, and redacted diagnostic code/message. Per-record safe failures may be retained separately without raw records or descriptions.

An `EmailIngestionEvent` records the n8n event's unique `messageId`, logical source, receive time, and safe aggregate result. Source-name lookup trims surrounding whitespace and is case-insensitive. Replaying the same ID is idempotent, cannot create a second set of jobs, and returns the stored acknowledgement with `replayed=true`. A structurally valid event with invalid individual jobs returns `200 OK`, an event status of `PARTIAL_SUCCESS`, aggregate counts, and safe per-job results. Request-level schema failures return `400 Bad Request`. The backend does not accept or retain a raw email body.

## Normalization and content safety

All sources use the same normalization pipeline:

- Unicode is normalized consistently.
- Leading/trailing and repeated whitespace is normalized without destroying meaningful punctuation.
- Company and title casing is preserved conservatively.
- Missing structured fields remain unknown; description prose is not mined for salary, skills, experience, or work arrangement.
- Provider HTML is parsed to text with an established HTML parser. Scripts, styles, markup, and unsafe control characters are removed.
- Description text is capped by configuration and marks `descriptionTruncated=true` when shortened.
- Currency codes and trusted timestamps are normalized. Missing or syntactically invalid optional codes, numbers, and timestamps become unknown rather than being guessed; invalid required identity, URL, or salary-range values produce a safe per-record failure.
- Complete descriptions are excluded from logs and audits, and React renders only the normalized plain-text value.

## URL canonicalization

The dedicated canonicalizer does not make network requests. It:

- accepts only `http` and `https`;
- lowercases the scheme and host;
- removes fragments and default ports;
- represents an empty path as `/` and removes trailing slashes from non-root paths as one deterministic URL-identity rule;
- removes only known tracking parameters such as `utm_*`;
- preserves other query parameters because they may identify a job;
- rejects malformed, unsupported, or ambiguous URLs.

Provider API request construction is stricter than job-link canonicalization: connector targets must remain on the fixed HTTPS allowlist through any redirect.

## Identity, updates, and exact deduplication

Deduplication is deterministic and ordered:

1. The database uniqueness rule `(source_id, external_id)` identifies a provider record. A repeated record updates the existing aggregate when its content hash changes and otherwise records `UNCHANGED`; both paths refresh `lastSeenAt`.
2. A new record with the same canonical application URL as an active job is persisted as `DUPLICATE` with `duplicateOfJobId`.
3. A SHA-256 fingerprint over normalized company, title, location, and canonical application URL host/path detects another exact duplicate.

The content hash covers normalized provider-controlled content and is separate from the identity fingerprint. Similar titles alone, or a similar title and employer with a meaningfully different location/URL, remain separate. A provider refresh never overwrites manual corrections; changed upstream content is retained as an available source update for review.

Manual ingestion accepts an optional idempotency key. Reusing it returns the original result instead of creating another job. All manual URLs are validated but never fetched.

## Run lifecycle and removal safety

Synchronization follows this lifecycle:

```text
create QUEUED run -> bounded executor -> mark RUNNING -> fetch outside transaction
                  -> normalize each record -> persist short transactional outcomes
                  -> finalize metrics and SUCCEEDED/PARTIAL_SUCCESS/FAILED
```

Overlapping active runs for one source are prevented. A per-source overlap returns `409 Conflict`; bulk `sync-enabled` responses reuse the current run identifier and status for an already-active source so n8n can continue polling it. Rejected executor submissions are persisted as failures. On startup, stale queued/running records are reconciled so they do not remain active forever. Run and source identifiers are propagated as worker diagnostic context and emitted only with safe lifecycle/metric logs.

Only a complete `SUCCEEDED` source snapshot advances missing counts. Failed or partial runs do not. Once the configured successful-run threshold is reached, an omitted active job becomes `SOURCE_REMOVED`. A reappearing job resets the counter and returns to `READY_FOR_EVALUATION` unless a user archived it. A job becomes `EXPIRED` only from a trusted structured expiry date or an explicit authenticated action; age alone is not an expiry signal.

## API summary

All user-facing endpoints require the existing single-user Basic authentication. The email event endpoint uses the existing n8n webhook-secret mechanism instead of Basic authentication.

| Area | Endpoints |
| --- | --- |
| Sources | `POST, GET /api/v1/job-sources`; `GET, PUT /api/v1/job-sources/{sourceId}`; enable/disable/archive actions |
| Synchronization | `POST /api/v1/job-sources/{sourceId}/sync`; `POST /api/v1/job-sources/sync-enabled` |
| Runs | `GET /api/v1/job-source-runs`; `GET /api/v1/job-source-runs/{runId}`; `GET /api/v1/job-sources/{sourceId}/runs` |
| Email alerts | `POST /api/v1/job-sources/email-alert/events` |
| Manual jobs | `POST /api/v1/jobs/manual` |
| Jobs | `GET /api/v1/jobs`; `GET, PUT /api/v1/jobs/{jobId}`; archive/restore/mark-expired actions |
| Duplicate review | `GET /api/v1/jobs/{jobId}/duplicates` |
| Dashboard counts | `GET /api/v1/jobs/summary` |

Source and job updates require the current record version and return `409 Conflict` for stale writes. Successful sync requests return `202 Accepted` with run identifiers. A per-source overlap returns `409 Conflict`, while `sync-enabled` returns the already-active run identifier/status alongside newly queued runs. Job listing supports bounded pagination, stable sorting, and filters for source, status, company, title, location, workplace/employment type, date ranges, and duplicate inclusion.

The summary endpoint returns database counts for total, ready, needs-review, duplicate, expired, source-removed, and archived jobs. It does not calculate a candidate match or score.

## Database migration

The next sequential Flyway migration adds or adapts:

```text
job_source_configuration
job_source_run
job_source_run_error
job_posting
email_ingestion_event
```

It uses UUID primary keys, UTC timestamps, explicit status/source checks, optimistic versions, a unique `(source_id, external_id)` provider identity, unique email message IDs, and a self-referencing duplicate foreign key. It indexes source, status, company, title, location, dates, fingerprint, and content hash. Source foreign keys do not use destructive cascades, so archival preserves jobs and run history. Previously applied V1 and V2 migrations are not edited.

Migration tests cover a clean database, upgrade from the Phase 2 chain, foreign-key/check failures, and duplicate identity/message constraints.

## Audit events

Phase 3 adds concise events for source creation/update/state changes, synchronization start/completion/failure, job creation/update/manual correction/duplicate/archive/restore/expiry/source removal, and email-event ingestion. Metadata contains identifiers, statuses, and counts only. Descriptions, remote payloads, email bodies, authentication headers, n8n secrets, and stack traces are excluded.

## Frontend

Authenticated routes are:

```text
/job-sources
/job-sources/:sourceId
/job-source-runs
/jobs
/jobs/new
/jobs/:jobId
```

The source UI accepts provider identifiers and a supported region, not a base URL. It displays enable/archive actions, last status, safe failures, run metrics, and manual synchronization. The run view polls queued/running records with a finite attempt count. The jobs view provides bounded pagination, filters, source/status badges, duplicate indicators, and manual entry. Details render normalized plain text, provenance, timestamps, update/duplicate state, and user-initiated external links with `target="_blank"` and safe `rel` attributes.

The dashboard uses `/api/v1/jobs/summary` for real job counts. Application and interview cards remain labelled future functionality.

## n8n workflows

The existing Phase 1 connectivity check remains documented. Phase 3 adds construction guides for:

- [scheduled enabled-source synchronization](../automation/n8n-workflows/phase-3-scheduled-sync.md);
- [structured email-alert mapping](../automation/n8n-workflows/phase-3-email-alert-mapping.md).

These guides intentionally do not contain credentials or personal email data. The scheduled guide names the backend environment variables but keeps their values in an n8n credential. The email guide requires immediate projection to approved fields plus an explicit execution-data saving/pruning policy because n8n may otherwise retain a trigger node's mailbox payload. The guides are not claimed to be importable workflow exports because no exported JSON has been validated against the installed n8n version.

## Security and SSRF controls

- Remote connectors resolve hosts from backend enums; an API request cannot supply a URL or secret.
- Connector requests use HTTPS and cannot redirect outside the exact provider allowlist.
- Manual, email-event, source, and application URLs are data only and are never fetched.
- Public provider GETs use no private ATS API key.
- Job descriptions are untrusted content and become bounded plain text before persistence/rendering.
- User-facing Phase 3 endpoints use Basic authentication; the email endpoint requires the environment-backed n8n secret and avoids redundant Basic authentication.
- Logs and audit records redact URLs where necessary and exclude secrets, headers, descriptions, raw responses, and email bodies.
- Production still requires HTTPS termination because Basic credentials are temporary single-user credentials.

## Commands and final verification

The supported final commands are:

```bash
./mvnw clean verify
npm ci
npm run lint
npm run test -- --run
npm run build
docker compose config
```

The pre-Phase-3 baseline was green: the backend had 24 tests with no failures, errors, or skips, and the frontend had 18 passing tests. The workspace does not contain a `.git` directory, so `git status` correctly reported that it was not a repository.

Final verification on Windows used the repository-supported wrapper and npm commands:

- `.\mvnw.cmd clean verify`: **106 tests**, 0 failures, 0 errors, 0 skips; build successful in 5 minutes 30 seconds. This included four Flyway scenarios against PostgreSQL, a real MinIO Testcontainer check, connector/HTTP fixtures served locally, normalization and deduplication tests, source-run recovery/checkpoint/overlap tests, Phase 3 API/security tests, and the retained Phase 1/2 suites.
- `npm.cmd ci`: successful; 352 packages installed. npm reported an upstream `eslint@9.39.5` deprecation notice and funding notices, but no install failure.
- `npm.cmd run lint`: successful with no lint warnings or errors.
- `npm.cmd run test -- --run`: **37 tests in 3 files**, all passing in 53.82 seconds. A prior unchanged retry was needed after transient Vitest worker-start timeouts during the cold run; the exact final run was green.
- `npm.cmd run build`: successful with TypeScript and Vite 7.3.6; 1,017 modules transformed. The 605.38 kB minified JavaScript bundle (187.76 kB gzip) produced Vite's non-failing 500 kB chunk-size advisory.
- `docker compose config --quiet`: successful.

Non-failing backend warnings were limited to Maven/Guice use of a terminally deprecated `sun.misc.Unsafe` method, an existing deprecated API use in `ProfileSnapshotService`, and Spring Boot's deprecated test `@MockBean` annotation.

## Runtime verification

`docker compose build backend frontend` and `docker compose up -d` built the verified application and started PostgreSQL, MinIO, backend, frontend, and n8n. No named volume was removed or reset. An unrelated pre-existing container already owned host port 5678, so this project's n8n container was started with a process-local `N8N_PORT=5679` override; the unrelated container was left untouched. Final Compose state showed all five project services healthy.

Runtime checks against the preserved Compose database verified:

- Flyway history contains successful V1, V2, and V3 entries, and Hibernate validates the schema. Runtime validation exposed an existing Phase 2 fixed-`CHAR` JDBC mapping mismatch; only the corresponding entity JDBC-type annotations were corrected, without altering an applied migration or stored data.
- An unauthenticated job-list request was denied with HTTP 403. An authenticated, clearly labelled manual fixture normalized HTML to `Safe plain text`, canonicalized its application URL to `https://jobs.example.com/openings/runtime?job=42`, became `READY_FOR_EVALUATION`, and returned the same UUID for the same idempotency key.
- After restarting only the backend container, the same job UUID and normalized content were returned from PostgreSQL.
- The authenticated candidate-profile endpoint returned the expected HTTP 404 because this clean local database has no profile yet; it did not fail with a server error. The MinIO live endpoint, frontend, backend actuator, and n8n health endpoints returned HTTP 200/`UP`.
- A valid structured email event was accepted with `SUCCEEDED`; replaying its `messageId` returned the same event with `replayed=true`; an invalid webhook secret returned HTTP 403.
- From inside the n8n container, `POST /api/v1/job-sources/sync-enabled` returned HTTP 202. It returned zero runs because the only enabled runtime source was the email webhook and no remote provider source was enabled.
- Lever and Greenhouse behavior was verified only with sanitized fixtures and the local HTTP test server. No live provider endpoint was called.

The clearly labelled runtime smoke records remain in the preserved local PostgreSQL volume so persistence can be inspected: one manual fixture, one email source, one email event, and its normalized job.

## Known limitations

- Lever does not document publication/update/expiry timestamps in the public Postings API field table, so the connector leaves those values unknown.
- Greenhouse jobs are delivered by an unpaged endpoint. Configured page size cannot reduce the upstream response; the response-byte limit is the safety boundary.
- Neither provider publishes a public-listing GET quota. Retry behavior is deliberately bounded and generic.
- Provider fields do not consistently include structured employment type, workplace type, country, team, salary, or expiry; missing values remain unknown.
- Exact rules intentionally leave uncertain near-duplicates unresolved rather than merging them.
- The n8n assets are construction guides, not version-validated importable exports.
- Basic authentication remains a temporary single-user mechanism and production requires external HTTPS termination.
- Live provider availability is not a build dependency; connector behavior is verified against sanitized fixtures and a local HTTP server.
- A simultaneous first use of the same manual idempotency key is protected by the database uniqueness rule, but the losing request can receive HTTP 409; retrying it returns the stored result.
- If the process is interrupted after an email event is recorded as `PROCESSING`, replay remains duplicate-safe but does not automatically resume that event; operational reconciliation is deferred.

## Deferred Phase 4 work

No candidate matching, scoring, profile-based filtering, skill or experience extraction, LLM call, embedding, vector database, tailored resume, cover letter, approval, submission, or application tracking is implemented in Phase 3. Later evaluation work must consume the active immutable candidate-profile version and normalized jobs through explicit module boundaries.

## Official provider references

- [Lever Postings API](https://github.com/lever/postings-api/blob/master/README.md)
- [Greenhouse Job Board API](https://developer.greenhouse.io/job-board.html)
- [Greenhouse jobs endpoint source documentation](https://github.com/grnhse/greenhouse-api-docs/blob/master/source/includes/job-board/_jobs.md)
- [Greenhouse board-token guidance](https://support.greenhouse.io/hc/en-us/articles/5888210160155-Find-your-job-board-URL)
