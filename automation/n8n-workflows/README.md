# Phase 1 backend connectivity

This phase documents the workflow instead of shipping an unverified n8n export.

1. Open n8n at `http://localhost:5678` and create a workflow.
2. Add a **Manual Trigger** node.
3. Add an **HTTP Request** node and connect it to the trigger.
4. Select `POST` and use `http://backend:8080/api/v1/system/n8n/ping` (inside Compose).
5. Add header `X-N8N-WEBHOOK-SECRET` with expression `{{ $env.N8N_WEBHOOK_SECRET }}`.
6. Execute the workflow and verify the JSON response has `status: ACKNOWLEDGED`, `source: n8n`, and a timestamp.

Do not paste the secret into a workflow export. The backend compares it to its environment-backed value and never logs it.

## Phase 3 workflow guides

Phase 3 continues the documentation-first approach. The following files are node-by-node construction guides, not exported workflow JSON and not claimed to be importable:

- [Scheduled enabled-source synchronization](phase-3-scheduled-sync.md)
- [Structured email-alert mapping](phase-3-email-alert-mapping.md)

The scheduled workflow uses an n8n-managed Basic-auth credential matching backend `APP_SECURITY_USERNAME` and `APP_SECURITY_PASSWORD` for authenticated user endpoints. The email mapping workflow uses `N8N_WEBHOOK_SECRET` only for the dedicated webhook endpoint, projects the trigger output immediately to approved fields, and requires an explicit n8n execution-data saving/pruning policy because node input/output may otherwise retain mailbox content. Neither guide contains a real credential, mailbox address, message body, or employer data.

## Phase 5 draft-package workflow

Phase 5 adds one importable, inactive-by-default workflow export:

- [Eligible application-package draft request](phase-5-eligible-application-package.json)
- [Setup, credentials, event contract, retry policy, and validation](phase-5-application-package-setup.md)

The export was import-validated with an ephemeral container of the Compose-pinned n8n `2.1.4` image. It contains unresolved credential placeholders instead of secrets and accepts only evaluation/job UUIDs through a Header-Auth webhook. It verifies the evaluation through an n8n-managed backend Basic-auth credential, uses a stable `Idempotency-Key` plus `X-N8N-Automation: true`, and has a visible maximum of three package-request attempts with 5- and 10-second waits. The marker makes the backend enforce its stricter automation policy; it is not a credential. Authentication, validation, quota/rate-policy, conflict, and other `4xx` results are terminal.

The workflow requests a backend-owned draft and records only safe operational status. It does not generate or inspect candidate content, access MinIO, browse a job board, approve a package, fill or submit an application, or send a message/email. Follow the setup guide and select local n8n credentials after import; never replace the placeholders in the committed JSON with real credential IDs or values.
