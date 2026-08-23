# Phase 3 structured email-alert mapping

This is a documentation-only construction guide. It is not an exported workflow and has not been validated as importable JSON against the installed n8n version.

## Purpose and trust boundary

n8n may hold a mailbox credential and transform a known job-alert template into a minimal structured event:

```text
Email Trigger
  -> select an approved alert template/source
  -> deterministically map approved job fields
  -> validate the minimal event
  -> POST to the backend with the n8n secret
  -> retain a safe acknowledgement
```

The backend never connects to Gmail or another mailbox, never receives a mailbox credential, and never needs the complete email body. This workflow must not use an LLM, browser automation, application-page fetch, or an ambiguous free-form extractor. If a message does not match a known deterministic template, route it for manual review without sending it to the backend.

## Prerequisites

1. Create the matching enabled `EMAIL_WEBHOOK` source configuration in the application. Its provider identifier is the logical `sourceName` used below; backend matching trims surrounding whitespace and is case-insensitive.
2. Configure the mailbox trigger through an n8n credential. Never place an OAuth token, password, refresh token, or mailbox address in an exported workflow.
3. Supply `N8N_WEBHOOK_SECRET` to n8n and the backend through their environment configuration. Do not type the value into the HTTP node.
4. Inside Compose, use `http://backend:8080/api/v1/job-sources/email-alert/events`.
5. Restrict the trigger/filter to an explicitly approved sender and alert format. Sender filtering alone is not proof that content is safe; all mapped values remain untrusted.

## Nodes

### 1. Email Trigger

Use the mailbox-specific trigger supported by the installed n8n version, configured with an n8n-managed credential. Request only the fields required to identify and map the alert. The trigger output may contain a body temporarily during execution, so treat the entire execution as sensitive: use the installed version's workflow/execution settings to disable saving successful/manual execution data where operationally acceptable, configure short pruning for retained failure data, and verify the policy before processing real mail. n8n can retain each node's input/output even when a later node omits a field.

### 2. Approved-template filter

Add an **If** or **Switch** node that checks the expected sender/domain and a stable template marker. Send unmatched messages to a manual-review/no-op branch. Do not attempt a best-effort extraction from arbitrary email.

### 3. Deterministic mapping

Use **Edit Fields**, **HTML Extract**, or a small deterministic **Code** node to map only the approved fields below. Configure the node to output only the mapped object instead of carrying the original trigger input forward:

- provider message ID;
- provider label, such as `GMAIL`;
- received timestamp;
- configured logical source name;
- per-job external ID when explicitly supplied;
- company, title, location, description, application URL, and publication timestamp when explicitly supplied by the approved template.

Do not map tracking pixels, unsubscribe text, sender signatures, unrelated links, quoted replies, attachments, or the complete body. Do not fetch an application URL. Confirm in a fictional execution that the output of this projection contains none of the original body, headers, attachments, or mailbox metadata beyond the approved fields.

If the template contains multiple jobs, emit one `jobs` array in a single event. If one mapped entry is incomplete, retain its safe structured fields so the backend can return a per-job failure; do not manufacture missing company, title, URL, or description values.

### 4. Build the event

Construct a payload matching this fictional example:

```json
{
  "messageId": "fictional-message-20260822-001",
  "provider": "GMAIL",
  "receivedAt": "2026-08-22T10:00:00Z",
  "sourceName": "Java Backend Alert",
  "jobs": [
    {
      "externalId": "fictional-provider-job-123",
      "company": "Example Company",
      "title": "Backend Engineer",
      "location": "Noida",
      "description": "Build and maintain backend services.",
      "applyUrl": "https://careers.example/jobs/123",
      "publishedAt": "2026-08-22T08:00:00Z"
    }
  ]
}
```

The example contains no real person, mailbox, employer credential, or application data. `messageId` must come from the provider and remain stable across delivery retries; it is the event idempotency key.

### 5. Validate before sending

Add an **If** node that rejects the event locally when:

- `messageId`, `receivedAt`, or `sourceName` is missing;
- no `jobs` array exists;
- the array exceeds the operator-approved maximum;
- the mapping unexpectedly contains a raw-body field, attachment, authentication value, or unrelated content.

Validation here reduces noise but does not replace backend validation.

### 6. Send the structured event

Add an **HTTP Request** node:

- Method: `POST`
- URL: `http://backend:8080/api/v1/job-sources/email-alert/events`
- Body content type: JSON
- Body: the mapped event only
- Header name: `X-N8N-WEBHOOK-SECRET`
- Header value expression: `{{ $env.N8N_WEBHOOK_SECRET }}`
- Response: JSON
- Retry on fail: off or tightly bounded only for a clearly failed connection

Do not add the user Basic-auth credential. The dedicated endpoint uses the n8n secret. An ambiguous connection failure may have occurred after the backend committed the event; if it is retried later, the unchanged `messageId` makes the replay idempotent.

### 7. Record a safe acknowledgement

Retain only the event ID, replay/status fields, and backend summary counts for workflow diagnostics. A suitable projection of the actual response is:

```json
{
  "eventId": "00000000-0000-0000-0000-000000000001",
  "messageId": "fictional-message-20260822-001",
  "replayed": false,
  "status": "SUCCEEDED",
  "jobCount": 1,
  "createdCount": 1,
  "updatedCount": 0,
  "unchangedCount": 0,
  "duplicateCount": 0,
  "failedCount": 0
}
```

Use the actual backend response schema. Do not retain the request body, descriptions, mailbox metadata, secret header, or full HTTP trace in a separate data store.

## Error handling

- `401` or `403`: the webhook secret is absent/incorrect. Stop and correct environment configuration; do not retry repeatedly.
- `404`: the trimmed, case-insensitive logical `sourceName` does not match an enabled email source, or the route is incorrect.
- `400`: the event envelope or request schema is invalid. Route only the backend's safe validation summary for manual review; do not attach the raw email.
- `200` with `replayed=true`: the message ID was already ingested. Treat the stored acknowledgement as success and never generate a new message ID to bypass idempotency.
- `200` with `status=PARTIAL_SUCCESS`: the event was accepted but one or more mapped jobs failed. Route the safe per-item `results` entries for review without the source email.
- `5xx` or connection failure: a later bounded retry must reuse the same provider `messageId`.

## Validation checklist

1. Send a fictional valid mapped event and verify the backend returns `200`, `replayed=false`, `SUCCEEDED`, and per-event counts.
2. Replay the identical `messageId` and verify the backend returns `200`, `replayed=true`, the stored counts, and no additional jobs.
3. Send an event with several fictional jobs, including one invalid item, and verify `200`, `PARTIAL_SUCCESS`, and safe per-job failure reporting.
4. Remove or alter the secret and verify rejection.
5. Inspect PostgreSQL/audit data and confirm no raw email body or unrelated content was stored. Inspect the n8n execution-data policy and a fictional execution to confirm the original body is projected away and retained only as briefly as explicitly required.
6. Confirm the workflow never requests any application URL.
7. If an export is created, inspect it for credentials, secrets, mailbox addresses, and sample personal content, then validate re-import against the installed n8n version before calling it importable.
