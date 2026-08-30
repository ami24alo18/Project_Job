# n8n JSearch integration

Status: implemented in the application and available as an importable workflow.

The workflow at [`n8n/workflows/jsearch-to-job-agent.json`](../n8n/workflows/jsearch-to-job-agent.json) replaces the Google Sheet storage node in the supplied workflow. It sends every mapped JSearch result (bounded to 100 jobs) to the application's replay-safe ingestion endpoint. PostgreSQL is the source of truth; a Sheet can be added later only as a reporting/export branch.

## 1. Create the application source

Use the **External automation** option in the Job sources UI when available, or call:

```http
POST /api/v1/job-sources/external
Authorization: Basic <application owner credentials>
Content-Type: application/json

{
  "displayName": "JSearch - India backend roles",
  "providerIdentifier": "jsearch-india-backend",
  "connectorType": "JSEARCH",
  "enabled": true
}
```

Copy the returned source ID and webhook token immediately. The application stores only the SHA-256 token digest and never shows the plaintext again. If it is lost, call `POST /api/v1/job-sources/{sourceId}/rotate-token` and update the n8n credential.

## 2. Import and configure n8n

1. Import `n8n/workflows/jsearch-to-job-agent.json` into n8n.
2. On **Fetch Jobs (JSearch)**, select or create an HTTP Header Auth credential containing the RapidAPI key. The workflow supplies `x-rapidapi-host` separately.
3. On **Send Batch to Job Agent**, create a different HTTP Header Auth credential:
   - header name: `X-Job-Agent-Webhook-Token`
   - header value: the one-time token returned during source creation or rotation.
4. In **Configure Source**, set:
   - `sourceId` to the application's external source UUID;
   - `jobAgentBaseUrl` to `http://host.docker.internal:8080` when n8n runs in Docker and the backend runs on Windows, or `http://localhost:8080` when both run directly on the host;
   - `query`, `datePosted`, and `maximumResults`; and
   - `searchRuleId` to an enabled application search rule UUID, or leave it empty.
5. Execute the workflow manually. Verify **Send Batch to Job Agent** returns a `SUCCEEDED` or `PARTIAL_SUCCESS` response and inspect the created `WEBHOOK`/`PUSH_BATCH` run in the application.
6. Activate the schedule only after the manual execution succeeds.

## Mapping and replay behavior

- The mapping node iterates over `data.jobs`; it does not select `data.jobs[0]`.
- Records without JSearch's stable `job_id` are not submitted because the application requires stable source identities.
- Employment and salary intervals are translated only to supported application enums. Unknown values become `UNSPECIFIED` rather than being guessed.
- `eventId` and `Idempotency-Key` both use the n8n execution ID plus source ID. A retry of the same execution returns the stored result; reuse with different content returns `409 Conflict`.
- The backend stores bounded per-record outcomes and provenance, not the raw provider payload or token.
- `PUSH_BATCH` runs never increment missing-job counters, because filtered JSearch results are not a complete inventory.

The removed Google Docs node was unused by the original mapping. The Google Sheets node wrote only `data.jobs[0]`, so it has intentionally been omitted from this ingestion path.
