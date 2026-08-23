# Phase 5 eligible application-package workflow

The importable [workflow export](phase-5-eligible-application-package.json) receives a trusted notification containing a completed Phase 4 evaluation UUID and job UUID, re-reads the evaluation from the backend, and requests an eligible application-package draft.

It does not generate content itself, inspect application pages, log in anywhere, fill or submit a form, approve an application, or send a recruiter message/email. The backend remains authoritative for ownership, current evaluation selection, automation enablement, quota, idempotency, fact validation, rendering, and private artifact storage.

The export targets the n8n `2.1.4` image pinned by `compose.yaml`. It is valid JSON and contains placeholder credential references with no credential value, candidate data, employer data, API key, or webhook secret. Validate import in the installed n8n instance before activation; import testing must not mount or modify a shared n8n data volume.

## Flow and bounds

```text
Authenticated webhook with evaluationId + jobId
  -> authenticated GET of the authoritative evaluation
  -> require SUCCEEDED, not stale, and APPLY or STRONG_APPLY
  -> POST {"evaluationId":"..."} to /api/v1/jobs/{jobId}/application-packages
     with Idempotency-Key: phase5-n8n-{evaluationId}
     and X-N8N-Automation: true
  -> retry only transport/500/502/503/504 responses
     after 5 seconds and then 10 seconds
  -> stop after at most three POST attempts
  -> emit only a safe accepted, ineligible, or stopped result
```

Authentication, authorization, validation, quota/policy, conflict, and all other `4xx` responses stop immediately. The current backend reports Phase 5 eligibility, automation-policy, and daily-quota rejections as `400`; they are terminal workflow results, not permission for a tight retry loop. The backend's durable package record is the source of truth after `202 Accepted`; this workflow does not poll or mutate its content.

Each generation POST has a 180-second request timeout and the workflow has a 600-second execution timeout. These bounds accommodate the backend's two bounded generation stages and deterministic rendering without permitting an indefinitely held execution. A transport timeout is ambiguous, so later attempts retain the same idempotency key.

## Prerequisites

1. Run the local stack and verify the backend health endpoint.
2. Publish an immutable candidate profile with eligible verified facts.
3. Complete a Phase 4 evaluation for an active job. Automation accepts only a current `APPLY` or `STRONG_APPLY` recommendation.
4. Set `APPLICATION_PACKAGE_AUTOMATION_ENABLED=true` in the backend environment only after testing the manual path. If provider-backed writing is required, set `OPENAI_API_KEY` only in the backend and explicitly set `OPENAI_CONTENT_GENERATION_ENABLED=true`.
5. Keep the MinIO bucket private and verify authenticated artifact downloads through the backend.

The backend does not expose its Basic-auth password to n8n automatically. n8n must store a separate credential containing the same configured local service identity.

## Create credentials in n8n

Open `http://localhost:5678` and create both credentials before activating the workflow. If another local n8n already owns port `5678`, set `N8N_PORT=5679` (or another free host port) in `.env`, recreate only the project n8n service, and use that host port instead; the container still listens on `5678` inside the Compose network.

### Job Agent backend

- Credential type: **HTTP Basic Auth**
- User: the backend `APP_SECURITY_USERNAME`
- Password: the backend `APP_SECURITY_PASSWORD`
- Suggested display name: `Job Agent backend`

### Phase 5 evaluation hook

- Credential type: **Header Auth**
- Header name: `X-PHASE5-WEBHOOK-SECRET`
- Header value: a new, high-entropy local secret dedicated to this hook
- Suggested display name: `Phase 5 evaluation hook`

The hook credential is owned by n8n. Do not reuse `OPENAI_API_KEY`, put the value in the workflow export, or add it to a Code node. Restrict network access to the n8n webhook in any non-local deployment and terminate HTTPS at the approved reverse proxy.

## Import and configure

Use the n8n UI's **Import from File** action and select `automation/n8n-workflows/phase-5-eligible-application-package.json`.

The Compose mount also makes the export available inside the n8n container at `/workflows/phase-5-eligible-application-package.json`. An operator who intentionally wants to persist it in the current local n8n database can run:

```bash
docker compose exec n8n n8n import:workflow --input=/workflows/phase-5-eligible-application-package.json
```

Importing changes the n8n database; do not use that command against a shared environment without its owner's authorization.

After import:

1. Open **Receive Completed Evaluation** and select the `Phase 5 evaluation hook` Header Auth credential.
2. Open **Fetch Authoritative Evaluation** and all three **Request Draft Attempt** nodes and select `Job Agent backend`.
3. Confirm every backend URL starts with `http://backend:8080` when n8n runs in this Compose network. For an external n8n deployment, replace the origin with the approved HTTPS backend origin in all four HTTP nodes.
4. Leave the stable idempotency expression and `X-N8N-Automation: true` header unchanged in every request attempt. The header is not a credential; it tells the authenticated backend to enforce its stricter automation policy. Replays of the same evaluation must use the same key.
5. Confirm the imported workflow retains `saveDataSuccessExecution: none`, `saveDataErrorExecution: none`, and `saveManualExecutions: false`. It projects the event to two UUIDs immediately and reduces backend responses to safe operational fields, but n8n can still hold active/waiting execution state until completion; configure pruning for any instance-level metadata that remains.
6. Save and activate only after the manual validation below passes.

Credential placeholder IDs in the JSON are intentionally unresolved. Selecting credentials in the UI replaces them; never edit a real credential ID or secret into the version-controlled export.

## Event contract

The production webhook accepts only a JSON object containing UUIDs:

```json
{
  "evaluationId": "00000000-0000-4000-8000-000000000051",
  "jobId": "00000000-0000-4000-8000-000000000061"
}
```

Those IDs are synthetic examples. Do not add candidate facts, contact information, job descriptions, application questions, model payloads, or credentials to the event. The workflow obtains the authoritative evaluation from the backend and immediately projects the trigger data to the two expected UUIDs. Completed success, error, and manual execution payload saving is disabled in the export.

The webhook responds immediately to avoid holding a caller connection open. An HTTP acknowledgement means n8n accepted the event, not that a package is ready. During a controlled manual test, inspect the transient safe terminal item; for activated runs, use the authenticated application-package API/UI because completed execution payloads are not retained.

For a local manual test, copy the workflow's production webhook URL from n8n and use a real local evaluation/job pair:

```bash
curl -X POST "http://localhost:5678/webhook/phase-5/evaluation-completed" \
  -H "Content-Type: application/json" \
  -H "X-PHASE5-WEBHOOK-SECRET: your-local-hook-secret" \
  -d '{"evaluationId":"YOUR_EVALUATION_UUID","jobId":"YOUR_JOB_UUID"}'
```

On PowerShell, use `Invoke-RestMethod` or escape JSON according to the shell. Never paste a real secret into a committed script or captured test fixture.

## Supplying events

The workflow deliberately starts with an authenticated webhook rather than scraping or polling job boards. A trusted Phase 4 orchestration, local operator, or another bounded internal workflow may post the two IDs after observing a completed evaluation. The current backend remains authoritative because the workflow immediately fetches `/api/v1/job-evaluations/{evaluationId}`, checks that the returned job ID matches the event, and sends that evaluation ID in every package request.

Do not connect an unauthenticated public source, mailbox body, job-board page, or arbitrary model output to this hook. Do not add notification or messaging nodes in Phase 5.

## Validation checklist

1. **Malformed event:** send a non-UUID value and verify **Normalize Event** stops before any backend package request.
2. **Mismatched IDs:** pair an evaluation with a different job and verify `EVENT_SOURCE_MISMATCH`.
3. **Ineligible recommendation:** use `MANUAL_REVIEW` or `SKIP` and verify the execution ends at **Record Safe Ineligible Result**.
4. **Stale/nonterminal evaluation:** verify the workflow ends without a generation request.
5. **Eligible evaluation:** verify exactly one request returns `200`, `201`, or `202` and emits only package ID/status and attempt count. Confirm the completed execution payload is not retained.
6. **Replay:** send the same event twice and verify the same `Idempotency-Key` is used and the backend does not create an equivalent duplicate revision.
7. **Policy failure:** disable automation or exhaust the configured quota and verify the first `4xx` ends at **Record Safe Failure** without a retry.
8. **Transient failure:** point a disposable test backend at controlled `503` responses and verify waits of 5 and 10 seconds and no fourth POST attempt.
9. **Credential and retention review:** export the configured workflow to a temporary location and inspect it before sharing. It must not contain credential values, candidate data, raw job text, generated content, API keys, or artifact URLs, and its success/error/manual execution-data settings must remain disabled.
10. **Artifact boundary:** after backend generation completes, download through the authenticated application endpoint. Verify that no workflow node contains a MinIO storage key or public object URL.

## Troubleshooting

- **Imported nodes show missing credentials:** expected. Select the two n8n-managed credentials; do not replace placeholders with secrets in the JSON file.
- **Backend URL fails inside Compose:** use `backend:8080`, not `localhost:8080`, from the n8n container.
- **Webhook returns an acknowledgement but no package appears:** during a controlled manual run, inspect the transient node result. For activated runs, inspect the authenticated backend package/evaluation state and safe n8n runtime status; completed execution payloads are intentionally not retained.
- **`401`/`403`:** correct the relevant Header Auth or Basic Auth credential and ownership. The workflow never retries these responses.
- **`400`/`409`:** inspect the backend's safe problem details and package/evaluation state. Phase 5 validation, eligibility, automation, and daily-quota policy failures currently return `400`; conflicts return `409`. Correct policy or source data and do not change the workflow to retry indefinitely.
- **Three transient attempts fail:** the workflow emits `TRANSIENT_RETRY_EXHAUSTED` during the run. Restore backend availability and send the same event again; the stable idempotency key protects an ambiguously accepted earlier request.

Stopping or deleting this workflow never deletes a package. Archiving a package is an explicit authenticated backend action, and neither operation is approval or submission.
