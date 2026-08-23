# Phase 3 scheduled source synchronization

This is a documentation-only construction guide. It is not an exported workflow and has not been validated as importable JSON against the installed n8n version.

## Purpose

Use n8n as the only scheduler for enabled Lever and Greenhouse source configurations:

```text
Schedule Trigger
  -> request sync-enabled
  -> retain returned run identifiers
  -> poll each run with a finite retry count
  -> notify only for FAILED or PARTIAL_SUCCESS
```

The backend owns source validation, overlap prevention, the bounded worker queue, retries against provider feeds, normalization, and run persistence. The workflow must not construct provider URLs or call Lever/Greenhouse directly.

## Prerequisites

1. Verify the existing Phase 1 backend-connectivity workflow first.
2. Set `APP_SECURITY_USERNAME` and `APP_SECURITY_PASSWORD` for the backend as described in the root [environment setup](../../README.md#environment-setup). In n8n, create an **HTTP Basic Auth** credential containing those same values. Give it a descriptive local name such as `Job Agent backend`.
3. Do not put either value in a node, expression, workflow export, or execution note; the environment variables configure the backend, while the reusable n8n credential configures HTTP nodes.
4. Inside Docker Compose, use `http://backend:8080` as the backend origin. If n8n runs outside Compose, use the operator-approved HTTPS reverse-proxy origin instead.
5. Choose a polling interval and finite maximum appropriate for the configured provider timeouts. The example below uses 10 seconds and 12 polls, a maximum polling window of approximately two minutes per run.

## Nodes

### 1. Schedule Trigger

Add a **Schedule Trigger** and select the desired local schedule. Avoid intervals shorter than the longest normal synchronization run. The backend rejects overlap, but a needlessly frequent trigger still creates operational noise.

### 2. Start enabled synchronizations

Add an **HTTP Request** node:

- Method: `POST`
- URL: `http://backend:8080/api/v1/job-sources/sync-enabled`
- Authentication: the n8n **HTTP Basic Auth** credential created above
- Response: JSON
- Request body: none
- Retry on fail: off; the backend records accepted runs, and an ambiguous transport retry could repeat the orchestration request

The endpoint returns `202 Accepted` with one run entry per enabled Lever/Greenhouse source. A newly accepted source has its queued run identifier/status; a source that was already active contributes its current run identifier/status so the workflow can poll the durable work instead of starting a duplicate. Use the actual response schema exposed by the running backend OpenAPI document; do not invent identifiers when the response is empty or invalid.

### 3. Split returned run identifiers

Add **Split Out** or **Item Lists** nodes, depending on the installed n8n version, to create one item per returned run identifier. Retain only operational fields such as:

```json
{
  "runId": "00000000-0000-0000-0000-000000000001",
  "pollAttempt": 0
}
```

The UUID is fictional. Do not retain source payloads, provider responses, Basic-auth headers, or job descriptions in workflow items.

If no run was queued, end successfully without notification.

### 4. Wait

Add a **Wait** node configured for 10 seconds. This prevents tight polling.

### 5. Fetch run status

Add an **HTTP Request** node:

- Method: `GET`
- URL expression: `http://backend:8080/api/v1/job-source-runs/{{ $json.runId }}`
- Authentication: the same n8n **HTTP Basic Auth** credential
- Response: JSON
- Retry on fail: off; handle the failure explicitly in the workflow

### 6. Check terminal state

Add a **Switch** node over the returned run status:

- `SUCCEEDED`: finish this item without notification.
- `PARTIAL_SUCCESS`: route to the failure/partial notification node.
- `FAILED`: route to the failure/partial notification node.
- `QUEUED` or `RUNNING`: increment `pollAttempt` and continue to the bounded-retry check.
- Missing or unknown status: treat as an operational failure; do not loop indefinitely.

### 7. Enforce the polling bound

Use an **Edit Fields** or **Code** node to increment `pollAttempt`, then an **If** node:

- When `pollAttempt < 12`, return to the **Wait** node.
- Otherwise, end the polling branch as a timeout and route one safe timeout notification.

The branch must have a visible numeric bound. Do not use an unbounded loop, recursive sub-workflow, or zero-delay polling.

### 8. Notify only on partial/failure

Connect only `PARTIAL_SUCCESS`, `FAILED`, malformed-status, request-failure, and polling-timeout branches to the operator's chosen notification integration. Include only safe operational data:

- run UUID;
- source UUID or display name if present in the run response;
- terminal status;
- discovered/created/updated/unchanged/duplicate/failed/removed counts;
- backend-provided safe error code/message.

Do not include job descriptions, raw provider bodies, stack traces, authentication headers, or credentials. A successful run should end silently.

## Error handling

- A per-source sync request returns `409` when that source already has an active run. `sync-enabled` handles that case inside its successful `202` response by returning the current run identifier/status for the source.
- A `401` indicates an invalid or missing n8n Basic-auth credential. Stop and correct the credential; do not retry in a loop.
- A backend `5xx` or network failure should produce one operational notification. Let the next scheduled execution retry later.
- Polling timeout does not change the backend run. The durable run remains the source of truth and stale-run reconciliation is a backend responsibility.

## Validation checklist

After constructing the workflow in the installed n8n UI:

1. Run it manually with no enabled remote sources and confirm it exits without notification.
2. Enable a fixture-backed or intentionally safe source, execute the workflow, and verify a `202` response and durable run ID. Trigger it again while that run is active and verify the same current run ID is returned rather than a second run.
3. Confirm polling stops on every terminal state.
4. Temporarily set a small poll limit and verify the workflow exits at that bound.
5. Confirm only failed/partial/timeout branches reach the notification node.
6. Export the workflow only if required, inspect the JSON for credentials or secrets, and validate re-import with the installed n8n version before describing it as importable.

Do not perform a live provider smoke test merely to validate n8n orchestration. The backend connector contract tests use local provider fixtures.
