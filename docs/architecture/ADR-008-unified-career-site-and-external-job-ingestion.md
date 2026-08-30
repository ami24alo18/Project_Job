# ADR-008: Unified career-site and external job ingestion

- Status: Accepted
- Date: 2026-08-25
- Extends: [ADR-003](ADR-003-authorized-job-ingestion.md)

## Context

The application currently ingests manual jobs, fixed-host Lever and Greenhouse public feeds, and structured email-alert events. Two additional acquisition paths are required:

1. an operator supplies an employer career-site URL and the application detects a supported applicant-tracking system (ATS), then synchronizes through an authorized provider adapter; and
2. an external automation such as n8n queries an aggregation API such as JSearch and pushes a bounded batch of normalized job candidates into the application.

These paths must converge on the existing normalization, exact identity, deterministic deduplication, lifecycle, audit, and matching boundaries. A career URL cannot become an unrestricted server-side request primitive, and an external batch cannot bypass the invariants owned by the `job` module.

The term "generic career site" therefore means generic onboarding and provider detection. It does not mean that the Spring application can execute arbitrary selectors, JavaScript, credentials, cookies, or user-defined HTTP requests.

## Decision

### Keep one ingestion boundary

All acquisition mechanisms produce a provider-neutral job candidate and call the existing `JobPostingService` ingestion boundary. Source-specific code may map provider data, but it cannot write `job_posting` directly or implement a second deduplication policy.

The dependency direction remains:

```text
jobsource -> job
jobsource -> audit
job       -> audit
```

Built-in pull connectors, signed external batches, email alerts, and an isolated extraction worker all terminate at the same normalization and persistence boundary.

### Separate source identity, connector identity, and provenance

`sourceType` remains backward compatible for existing API consumers and stored records. It gains `EXTERNAL_API` and `CAREER_SITE` values. Existing `LEVER`, `GREENHOUSE`, and `EMAIL_WEBHOOK` values are not rewritten.

A source configuration also exposes a `connectorType`:

| Source type | Connector examples | Behavior |
| --- | --- | --- |
| `LEVER` | `LEVER` | Backend pull from the fixed Lever allowlist |
| `GREENHOUSE` | `GREENHOUSE` | Backend pull from the fixed Greenhouse allowlist |
| `CAREER_SITE` | `ORACLE_CX`, `WORKDAY`, `SMARTRECRUITERS`, `GENERIC_JSON_LD`, `CUSTOM_RECIPE` | Detected career source; activation depends on support status |
| `EXTERNAL_API` | `JSEARCH`, `JOBSPY`, `CUSTOM_WEBHOOK` | An external system pushes bounded structured events |
| `EMAIL_WEBHOOK` | `EMAIL` | n8n pushes a structured email-alert event |

The API derives a source category (`PULL_FEED`, `PUSH_WEBHOOK`, or `EMAIL_WEBHOOK`) from the source and connector capabilities rather than trusting a client-supplied category.

Job provenance distinguishes how a record arrived from where it was originally published:

- `ingestionProvider`: the system that delivered the record, such as `JSEARCH`, `JOBSPY`, `LEVER`, `GREENHOUSE`, or `CUSTOM_RECIPE`;
- `originPublisher`: an optional upstream publisher such as `LINKEDIN`, `INDEED`, or an employer career site;
- `externalId`: stable identity within the configured source;
- `sourceUrl` and `applyUrl`: validated links stored as data and never followed by the job module;
- `discoveryQuery`: the bounded query or rule that produced a filtered result; and
- `externalEventId`: the push event that delivered the record, when applicable.

Provider names are bounded plain identifiers. They do not grant permission to call a provider or imply endorsement.

### Share search rules without making them identity

A source may own versioned search rules containing query text, locations, workplace preferences, a date window, and bounded result/page limits. Pull connectors use only fields they support. External automation may retrieve the same enabled rules and translate them into provider parameters.

Search rules affect acquisition volume; they do not replace candidate matching. A job is normalized and persisted before the existing filtering and evaluation modules decide candidate relevance. Query text is provenance and is excluded from provider identity, content hashes, and duplicate fingerprints.

### Record run coverage explicitly

Every source run records one coverage value:

- `COMPLETE_INVENTORY`: the connector proved that it traversed the complete configured provider inventory;
- `FILTERED_QUERY`: the source returned only records matching a query or remote filter; or
- `PUSH_BATCH`: an external event delivered a bounded set without claiming inventory completeness.

Only a `SUCCEEDED` `COMPLETE_INVENTORY` run may advance missing-job counters. `FILTERED_QUERY`, `PUSH_BATCH`, partial, failed, page-limited, response-limited, or tainted resumed runs never mark an unseen job as removed. This rule is enforced by the backend and cannot be overridden by n8n or a request field.

### Authenticate external batches per source

An `EXTERNAL_API` source receives a cryptographically random opaque webhook token. The plaintext is shown once; only a SHA-256 token hash is stored. The token is sent in `X-Job-Agent-Webhook-Token`, compared in constant time, kept in an n8n credential, excluded from application logs, and rotatable by an authenticated user. HTTPS is mandatory outside the trusted local Compose network.

Each event has a client-generated `eventId` unique within its source plus a server-computed canonical payload checksum. Repeating the same event and checksum returns the stored result. Reusing an event ID with different content returns `409 Conflict`. One request accepts at most 100 jobs and is subject to a configured request-byte limit.

Valid jobs in a structurally valid event are processed independently. Invalid records produce bounded safe errors and a `PARTIAL_SUCCESS` result without discarding valid jobs. Raw provider responses, complete descriptions, tokens, credentials, and request headers are not stored in event or audit records.

The event stores aggregate metrics and one bounded result row per submitted job so an identical replay can return the original actions and safe errors without reprocessing. Result rows contain the request index, external ID, optional persisted job ID, action or safe error, and no description or raw payload.

### Make career URL discovery a guarded registration operation

Discovery accepts one operator-supplied HTTPS career URL and returns a canonical URL, detected connector, and support status. It does not create or enable a source.

The discovery client:

- rejects non-HTTPS URLs, user information, fragments, literal IP targets, and non-default or unapproved ports;
- resolves DNS and rejects loopback, private, link-local, multicast, documentation, reserved, and cloud-metadata destinations for every address;
- pins the validated addresses for the request and repeats validation for every redirect;
- applies strict connect/request timeouts, redirect, response-byte, and decompression limits;
- sends no operator-defined headers, cookies, credentials, or browser state;
- never sends authorization across redirects; and
- records only bounded detection evidence, never the complete response.

Detection uses a versioned backend registry of host/path and bounded markup fingerprints. The initial support states are `SUPPORTED`, `NEEDS_AUTHORIZATION`, `NEEDS_ADAPTER`, `NEEDS_EXTRACTION_RECIPE`, `UNSUPPORTED`, and `VALIDATION_FAILED`.

Discovery is not authorization. A detected source can run only when its connector and support state permit activation.

### Keep browser extraction outside the application network

Documented fixed-host public APIs remain the preferred path. Public static pages with structured `JobPosting` JSON-LD may be handled by a bounded built-in adapter after host validation and an explicit source test.

JavaScript rendering or site-specific extraction executes only in a separately deployed worker with an explicit hostname allowlist and versioned recipe. The worker has internet egress but no route to PostgreSQL, MinIO, the Docker control socket, cloud metadata, or private application endpoints other than the narrow external-event ingress. It holds its source token in its credential store and pushes normalized batches through the `EXTERNAL_API` contract.

Unsupported or permission-limited sites remain disabled. Operators cannot upload executable selectors, scripts, browser profiles, cookies, or request headers through the Job Agent API. A recipe is reviewed repository configuration, not arbitrary database content.

### Preserve scheduling ownership

Spring owns durable run state and bounded worker execution, but not schedules. n8n continues to own scheduled triggers, JSearch calls, optional extraction workflows, finite polling, and notifications. Google Sheets may be an optional audit/reporting branch; it is not the system of record and is not an ingestion dependency.

## Consequences

- Design 1 and Design 2 share normalization, deduplication, provenance, metrics, replay, and UI concepts.
- Adding a built-in ATS still requires a documented or authorized interface, fixed endpoint policy, fixtures, and connector contract tests.
- Career URL acceptance is broader than ADR-003's original provider-identifier form, so discovery and execution are separated and guarded by explicit support states.
- Push and filtered sources deliberately retain stale records rather than making an unsafe absence claim.
- Per-source token rotation invalidates the prior n8n credential and requires an explicit workflow credential update.
- Cross-source duplicates remain preserved and linked rather than merged destructively.

## Alternatives considered

An unrestricted URL connector was rejected because it creates SSRF, redirect, resource-exhaustion, and executable-content risks. A universal CSS-selector configuration was rejected because it turns stored configuration into executable scraping logic. Reading Google Sheets back into the application was rejected because it loses delivery idempotency, per-record diagnostics, source-run state, and accurate provenance. Giving JSearch direct database access was rejected because it bypasses normalization and audit boundaries. Treating every filtered query as a complete scan was rejected because it would incorrectly remove jobs found by other queries.

## References

- [ADR-003: Authorized public job ingestion](ADR-003-authorized-job-ingestion.md)
- [Unified job-source API contract](../unified-job-source-ingestion-contract.md)
- [Career-site discovery threat model](../career-site-discovery-threat-model.md)
- [Oracle Recruiting CE Job Requisitions](https://docs.oracle.com/en/cloud/saas/human-resources/farws/api-recruiting-ce-job-requisitions.html)
- [Oracle Job Site Posted Jobs](https://docs.oracle.com/en/cloud/saas/human-resources/farws/api-job-site-posted-jobs.html)
- [LinkedIn User Agreement](https://www.linkedin.com/legal/user-agreement)
- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
