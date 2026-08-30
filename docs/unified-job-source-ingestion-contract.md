# Unified job-source ingestion contract

- Status: Implemented; browser extraction runtime remains a separately deployed, disabled-by-default extension
- Date: 2026-08-25
- Architecture: [ADR-008](architecture/ADR-008-unified-career-site-and-external-job-ingestion.md)

## Scope

This contract defines the additive APIs and invariants for employer career-site onboarding and external job batches. Existing manual, Lever, Greenhouse, email-alert, job, and run APIs remain backward compatible.

The contract intentionally separates:

- acquisition filters from candidate matching;
- configured source identity from upstream publisher provenance;
- career-site discovery from source activation;
- a complete inventory scan from a filtered query or push batch; and
- a safe structured webhook from an arbitrary remote-fetch request.

## Common enums

### Source type

```text
LEVER | GREENHOUSE | EMAIL_WEBHOOK | EXTERNAL_API | CAREER_SITE
```

`MANUAL` remains a job source type but has no `JobSourceConfiguration`.

### Connector type

```text
LEVER | GREENHOUSE | EMAIL | JSEARCH | JOBSPY | CUSTOM_WEBHOOK |
ORACLE_CX | WORKDAY | SMARTRECRUITERS | GENERIC_JSON_LD | CUSTOM_RECIPE
```

### Support status

```text
SUPPORTED | NEEDS_AUTHORIZATION | NEEDS_ADAPTER |
NEEDS_EXTRACTION_RECIPE | UNSUPPORTED | VALIDATION_FAILED
```

### Run coverage

```text
COMPLETE_INVENTORY | FILTERED_QUERY | PUSH_BATCH
```

The backend derives coverage from the connector execution path. Clients cannot assert `COMPLETE_INVENTORY`.

## Source representation

Existing source fields remain. The response gains:

```json
{
  "id": "uuid",
  "displayName": "JSearch - India backend roles",
  "sourceType": "EXTERNAL_API",
  "sourceCategory": "PUSH_WEBHOOK",
  "connectorType": "JSEARCH",
  "providerIdentifier": "jsearch-india-backend",
  "careerSiteUrl": null,
  "canonicalHost": null,
  "supportStatus": "SUPPORTED",
  "supportMessage": null,
  "enabled": true,
  "pageSize": 50,
  "maximumPagesPerRun": 10,
  "missingRunThreshold": 2,
  "webhookConfigured": true,
  "lastSuccessfulSyncAt": null,
  "lastAttemptedSyncAt": null,
  "consecutiveFailureCount": 0,
  "recordVersion": 0,
  "createdAt": "2026-08-25T12:00:00Z",
  "updatedAt": "2026-08-25T12:00:00Z",
  "archivedAt": null
}
```

The token hash, discovery response body, DNS results, redirect trace, credentials, and extraction implementation details are never serialized.

## External source creation

```http
POST /api/v1/job-sources/external
Authorization: Basic ...
Content-Type: application/json
```

Request:

```json
{
  "displayName": "JSearch - India backend roles",
  "providerIdentifier": "jsearch-india-backend",
  "connectorType": "JSEARCH",
  "enabled": true
}
```

Successful creation returns `201 Created`:

```json
{
  "source": { "id": "uuid", "sourceType": "EXTERNAL_API", "connectorType": "JSEARCH" },
  "webhookUrl": "/api/v1/job-sources/uuid/external-events",
  "webhookToken": "plaintext-shown-once"
}
```

The plaintext token is returned only from creation and rotation. A lost token must be rotated.

## Token rotation

```http
POST /api/v1/job-sources/{sourceId}/rotate-token
Authorization: Basic ...
```

Returns `200 OK` with a new one-time plaintext token. Rotation commits before returning; the previous token immediately becomes invalid. Rotation of a disabled source is allowed so an operator can prepare credentials, but archived or non-external sources return `409 Conflict`.

## Search rules

```http
GET  /api/v1/job-sources/{sourceId}/search-rules
POST /api/v1/job-sources/{sourceId}/search-rules
PUT  /api/v1/job-sources/{sourceId}/search-rules/{ruleId}
POST /api/v1/job-sources/{sourceId}/search-rules/{ruleId}/enable
POST /api/v1/job-sources/{sourceId}/search-rules/{ruleId}/disable
```

Rule request:

```json
{
  "name": "Java backend - India",
  "query": "Java Backend Developer",
  "locations": ["India"],
  "remoteAllowed": true,
  "hybridAllowed": true,
  "onsiteAllowed": true,
  "datePostedWindow": "TODAY",
  "maximumResults": 100,
  "enabled": true,
  "recordVersion": 0
}
```

Rules are source-scoped. Query and name are trimmed, lists are normalized case-insensitively, at least one workplace choice is required, and maximum results are bounded by configuration. Provider-specific parameters are not accepted as arbitrary maps; supported translations belong to connector code or reviewed n8n workflow mappings.

## External batch ingestion

Persistence uses `external_ingestion_event` for source/event identity, checksum, lifecycle, query provenance, and aggregate counters, plus `external_ingestion_event_result` for the bounded response row associated with each submitted index. The result table stores only index, external ID, optional job ID, action, and safe error code/message. This makes exact replay deterministic without retaining raw provider records.

```http
POST /api/v1/job-sources/{sourceId}/external-events
X-Job-Agent-Webhook-Token: <source token>
Idempotency-Key: <same value as eventId>
Content-Type: application/json
```

Basic authentication is neither required nor accepted as a substitute for the source token on this endpoint.

Request:

```json
{
  "eventId": "n8n-12345-java-india-2026-08-25",
  "ingestionProvider": "JSEARCH",
  "searchRuleId": "optional-uuid",
  "query": "Java Backend Developer in India",
  "fetchedAt": "2026-08-25T12:00:00Z",
  "jobs": [
    {
      "externalId": "provider-job-id",
      "originPublisher": "LINKEDIN",
      "company": "Example Company",
      "title": "Senior Java Backend Developer",
      "location": "Bengaluru, India",
      "countryCode": "IN",
      "workplaceType": "HYBRID",
      "employmentType": "FULL_TIME",
      "department": null,
      "team": null,
      "description": "Untrusted provider description",
      "applyUrl": "https://example.test/apply/123",
      "sourceUrl": "https://example.test/jobs/123",
      "salaryMinimum": null,
      "salaryMaximum": null,
      "salaryCurrency": null,
      "salaryInterval": "UNSPECIFIED",
      "publishedAt": "2026-08-25T09:00:00Z",
      "expiresAt": null
    }
  ]
}
```

Request constraints:

- `eventId`: required, 1-300 safe identifier characters;
- `Idempotency-Key`: required and exactly equal to `eventId`;
- `ingestionProvider`: required and must match the configured source connector;
- `query`: optional, maximum 500 characters;
- `jobs`: 1-100 records;
- each job requires a stable `externalId`, company, and title;
- descriptions and URLs use the existing job normalization limits; and
- unknown fields fail the request under the existing strict Jackson policy.

Response for a new or replayed event is `200 OK`:

```json
{
  "eventId": "n8n-12345-java-india-2026-08-25",
  "runId": "uuid",
  "replayed": false,
  "status": "PARTIAL_SUCCESS",
  "discoveredCount": 2,
  "createdCount": 1,
  "updatedCount": 0,
  "unchangedCount": 0,
  "duplicateCount": 0,
  "failedCount": 1,
  "results": [
    { "index": 0, "externalId": "provider-job-id", "jobId": "uuid", "action": "CREATED" },
    { "index": 1, "externalId": "bad-id", "errorCode": "INVALID_JOB", "errorMessage": "The mapped job could not be normalized or stored" }
  ]
}
```

The response is bounded to the request's 100 records and never echoes descriptions, tokens, headers, or raw provider data.

### Replay and conflict semantics

- Same source, event ID, and canonical payload checksum: return the stored response with `replayed=true`.
- Same source and event ID but a different checksum: `409 Conflict`.
- Disabled, archived, wrong-type, or connector-mismatched source: `409 Conflict`.
- Missing or invalid source token: `403 Forbidden` with no indication of whether a source exists.
- Structurally invalid request: `400 Bad Request` with field errors.
- Request above the configured byte limit: `413 Payload Too Large`.

External events execute synchronously within the HTTP request but use short per-record transactions. They create a durable run with `triggerType=WEBHOOK` and `coverage=PUSH_BATCH`. They never perform missing-job accounting.

## Career-site discovery

```http
POST /api/v1/job-sources/discover
Authorization: Basic ...
Content-Type: application/json
```

Request:

```json
{
  "companyName": "American Express",
  "careerSiteUrl": "https://careers.americanexpress.com/en/sites/CX_1"
}
```

Response is `200 OK` for a safely inspected URL, including unsupported results:

```json
{
  "canonicalUrl": "https://careers.americanexpress.com/en/sites/CX_1",
  "canonicalHost": "careers.americanexpress.com",
  "connectorType": "ORACLE_CX",
  "providerIdentifier": "CX_1",
  "supportStatus": "NEEDS_AUTHORIZATION",
  "supportMessage": "The detected Oracle integration is not enabled without an approved interface",
  "detectionVersion": "career-site-detection-v1"
}
```

Unsafe URLs return `400 Bad Request` with a safe validation message. DNS, timeout, size, redirect, or remote availability failures return `422 Unprocessable Entity` or `503 Service Unavailable` with a bounded safe code. Responses never expose resolved addresses or internal networking details.

Discovery is read-only. Creation is a separate authenticated request that repeats validation and requires `supportStatus=SUPPORTED` unless it creates a disabled source for a reviewed external recipe.

## Career-source creation and test

```http
POST /api/v1/job-sources/career-site
POST /api/v1/job-sources/{sourceId}/test
```

Creation accepts the company display name, original career URL, normal source bounds, and requested enabled state. It deliberately does not accept a connector, detection version, or detection result from the browser. The backend repeats canonicalization and detection and enables only a currently supported built-in adapter.

`test` performs one bounded connector-specific request without persisting jobs or changing missing counters. It records a safe attempt timestamp/result and returns connector capability information. It cannot execute a `CUSTOM_RECIPE` inside the Spring process.

A source detected as `CUSTOM_RECIPE` is initially disabled with `NEEDS_EXTRACTION_RECIPE`. A deployment may enable reviewed recipes in backend configuration and associate only their identifier:

```http
POST /api/v1/job-sources/{sourceId}/recipe
Content-Type: application/json

{ "recipeId": "approved-company-jobs", "enabled": true }
```

The registry fixes the exact host, allowed path prefixes, version, and enabled state. A successful association returns the source-scoped external-event URL and a one-time plaintext token. The separately isolated worker uses that token to push `CUSTOM_RECIPE` events; Spring stores the recipe version as provenance but never receives or executes selectors, scripts, headers, cookies, or browser state.

## Run contract

Run responses gain:

```json
{
  "triggerType": "WEBHOOK",
  "coverage": "PUSH_BATCH",
  "searchRuleId": "optional-uuid",
  "externalEventId": "optional-uuid"
}
```

Valid trigger values become:

```text
MANUAL | N8N | RETRY | WEBHOOK | EXTRACTION_WORKER
```

Only `SUCCEEDED + COMPLETE_INVENTORY` invokes `markMissingAfterSuccessfulRun`. Coverage is visible in the UI so an operator can distinguish a complete provider refresh from a query result.

## Job response provenance

Job responses gain:

```json
{
  "ingestionProvider": "JSEARCH",
  "originPublisher": "LINKEDIN",
  "discoveryQuery": "Java Backend Developer in India",
  "externalEventId": "optional-uuid"
}
```

These fields are filterable only after supporting indexes and bounded query parameters are defined. They are display/audit provenance and do not alter exact provider identity or duplicate fingerprints.

## Audit and logging

New audit events cover external-source creation, token rotation, event ingestion, career discovery, connection testing, and recipe association. Metadata contains IDs, connector/support status, checksums, and counts only.

The following are forbidden in logs and audit metadata:

- webhook tokens or token hashes;
- RapidAPI keys or other provider credentials;
- authorization and cookie headers;
- complete query responses or HTML;
- job descriptions;
- resolved IP addresses visible to ordinary users; and
- extraction browser state.
