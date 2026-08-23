# Phase 5 — verified application-package drafts

Phase 5 turns a completed Phase 4 job evaluation into a traceable draft package: tailored resume content, a cover letter, a recruiter-message draft, classified application-question drafts, claim evidence, and deterministic HTML, PDF, and DOCX resume artifacts.

> **Draft boundary:** `READY` means generated and validated. It does not mean approved or submitted. Phase 5 does not browse a job board, log in to an external service, fill a form, send a message or email, bypass a CAPTCHA, approve an application, or submit one.

The architectural rationale is recorded in [ADR-005](architecture/ADR-005-verified-application-content-and-deterministic-document-generation.md).

## Source and revision model

An `ApplicationPackage` is the stable candidate/job container. Every generation or regeneration creates an immutable-source `ApplicationPackageRevision`; historical revisions are retained. A revision records the exact:

- job posting and normalized job checksum;
- immutable candidate profile version and checksum;
- completed Phase 4 evaluation and checksum;
- generation prompt and JSON Schema versions;
- provider and model identity;
- resume-template version and generation settings.

Package states are `REQUESTED`, `GENERATING`, `READY`, `FAILED`, `STALE`, and `ARCHIVED`. A generating revision may progress through `PLANNING`, `VALIDATING`, and `RENDERING` before becoming ready or failed. There is deliberately no `APPROVED` state.

Structured generated content is stored separately from claim atoms, claim-to-fact sources, question drafts, and artifact metadata. Resume and application binaries are never stored in PostgreSQL. A user edit is marked `USER_EDITED` and does not retain automatic `VERIFIED` status unless it passes revalidation.

Flyway `V5` introduces package/revision, structured content, claim provenance, question-draft, artifact, and generation-execution metadata. `V6` adds generation-settings identity, the original revision idempotency hash, and exact LLM source checksums. `V7` normalizes hashed idempotency aliases into a many-to-one table so every accepted key remains bound to the exact historical revision it returned. The migrations are additive and preserve earlier Phase 1–4 data.

## Generation flow

1. The authenticated API validates that the job is active, the selected Phase 4 evaluation is complete, its exact immutable profile version still exists, and eligible verified facts belong to that version.
2. It creates or returns an idempotent package request using source checksums and generation versions. Equivalent concurrent requests cannot create duplicate current revisions.
3. Stage A creates a structured tailoring plan containing selected fact and requirement IDs, skill/bullet ordering, unsupported requirements, omissions, and emphasis. It does not persist final prose.
4. The server validates every selected identifier and source boundary.
5. Stage B generates strict structured content from the validated plan and selected sources only.
6. The server validates claim evidence and factual atoms. A single bounded repair may address schema or provenance errors. A second invalid result fails closed.
7. A deterministic, repository-owned template renders an HTML preview, selectable-text ATS PDF, and ATS-friendly DOCX.
8. Valid artifacts are checksummed and stored privately in MinIO. The package becomes a `READY` draft.

Regeneration always creates another revision. It never silently rebinds an old revision to a new profile/evaluation or overwrites user-edited content. Replacement of edits requires an explicit content-replacement choice; that confirmation is not formal application approval.

## Fact grounding and prompt-injection resistance

Only facts from the revision's exact immutable profile version and an eligible verification state can support candidate claims. Every candidate factual claim has one or more source fact IDs. The backend rejects nonexistent or wrong-version IDs, missing evidence, unsupported numeric values or dates, unsupported organizations and titles, named technologies absent from the referenced facts, duplicate or conflicting claims, prohibited sensitive content, malformed output, and configured length-limit violations.

Job requirements are evidence about the role, not evidence about the candidate. A missing skill is reported as an unsupported requirement; it is never promoted into the candidate's resume. Metrics cannot be improved or estimated, and missing information remains missing. Experience answers use the exact immutable snapshot's `totalExperienceMonths` value and format it deterministically; individual employment date ranges are preserved rather than estimated or recomputed.

Job descriptions, employer text, and application questions are untrusted data. They are passed in bounded, delimited fields with instructions that they cannot alter the system policy, output schema, evidence rules, or tool behavior. The provider has no tools, web search, file search, or external-action capability. Provider-side response storage is disabled where supported. Raw prompts, responses, generated documents, facts, answers, and contact details are not logged by default.

Candidate name, contact details, location, and profile links are injected deterministically during rendering and are not sent to the model. Sensitive candidate fields are excluded from generation requests.

## Application-question policy

Questions may be entered manually or supplied by an already compliant import path; Phase 5 does not scrape forms.

| Classification | Behavior |
| --- | --- |
| `VERIFIED_AUTOMATIC` | Answered deterministically from eligible verified facts; no model call is needed. |
| `SUGGESTED_REQUIRES_REVIEW` | A low-risk subjective draft may be generated, with candidate claims still tied to evidence. |
| `USER_INPUT_REQUIRED` | No inference when compensation, availability, relocation, notice period, authorization, sponsorship, declaration, consent, or another current choice is missing. |
| `SENSITIVE_NEVER_AUTOMATIC` | Demographic, health/disability, veteran, criminal-history, legally binding, and other protected or consent questions are classified but left unanswered. |

## REST API

All endpoints below use the existing Basic authentication, ownership checks, validation/problem-details responses, and audit conventions. An artifact download streams through the backend and never returns a MinIO credential, bucket key, public object URL, or signed URL. The current application does not add an HTTP correlation-ID contract.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/jobs/{jobId}/application-packages` | Request a draft. Send `{}` to select the latest completed evaluation, or an optional `evaluationId` and recorded manual `overrideReason`. Supports `Idempotency-Key`; trusted n8n calls also send `X-N8N-Automation: true`. |
| `GET` | `/api/v1/application-packages` | List owned packages with `page`, `size`, and safe `sort` controls, optionally filtered by `status` and `stale`. |
| `GET` | `/api/v1/application-packages/{packageId}` | Read package summary/current revision. |
| `GET` | `/api/v1/application-packages/{packageId}/revisions` | List historical revisions. |
| `GET` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}` | Inspect structured content, evidence, warnings, and source versions. |
| `POST` | `/api/v1/application-packages/{packageId}/regenerate` | Create a new revision. Supports `Idempotency-Key`; replacing edits must be explicit. |
| `PUT` | `/api/v1/application-packages/{packageId}/content/{contentId}` | Edit one structured content item with optimistic locking. |
| `POST` | `/api/v1/application-packages/{packageId}/validate` | Revalidate draft content and evidence. |
| `POST` | `/api/v1/application-packages/{packageId}/questions` | Add a bounded manual application question. |
| `POST` | `/api/v1/application-packages/{packageId}/questions/draft` | Classify questions and draft only permitted answers. |
| `GET` | `/api/v1/application-packages/{packageId}/resume/preview` | Return the protected HTML preview. |
| `GET` | `/api/v1/application-packages/{packageId}/resume/pdf` | Download the protected PDF resume. |
| `GET` | `/api/v1/application-packages/{packageId}/resume/docx` | Download the protected DOCX resume. |
| `POST` | `/api/v1/application-packages/{packageId}/archive` | Archive a package without deleting revision history or artifacts. |

Use the running OpenAPI document at `/v3/api-docs` for exact request and response fields. A generation request returns `202 Accepted` with a package resource and a `Location` header after the current bounded generation call returns. Durable intermediate states can still be observed by a concurrent reader. After an ambiguous disconnect, fetch the package or repeat the POST with the same idempotency key; do not introduce a new key merely because the original response was lost.

## Eligibility, idempotency, and staleness

Manual generation requires a completed Phase 4 evaluation and may use any recommendation. The authenticated actor is recorded; an optional `overrideReason` records why an operator chose a lower recommendation.

Automated n8n generation additionally requires:

- recommendation `STRONG_APPLY` or `APPLY`;
- a non-stale evaluation and available exact profile version;
- `APPLICATION_PACKAGE_AUTOMATION_ENABLED=true`;
- remaining daily generation quota; and
- no equivalent current package.

The backend enforces these conditions even if a workflow sends an incorrect request. Cache identity includes candidate ownership, job/evaluation/profile identities and checksums, prompt/schema/model/template versions, and generation settings. Hashed idempotency-key aliases remain bound to the exact revision originally returned, including when a new key reused an equivalent cached revision; raw keys are never stored, and aliases are never shared across candidates.

A material job change, replacement or staleness of the evaluation, change of active profile version, archived source, or applicable generation/template policy change marks the package stale. The historical revision remains readable and keeps its original provenance; regeneration is explicit.

## Rendering and private artifact storage

The renderer uses a single-column template with clear headings, standard fonts, stable section order, selectable text, predictable spacing, and no photos, skill bars, essential graphics, macros, scripts, or external resources. It sanitizes portable filenames in the form `FirstName_LastName_Role_Company_YYYYMMDD.ext`.

Artifact types and MIME types are:

| Artifact | MIME type |
| --- | --- |
| `HTML_PREVIEW` | `text/html;charset=UTF-8` |
| `PDF_RESUME` | `application/pdf` |
| `DOCX_RESUME` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

At runtime, every artifact must have the expected type/extension, nonempty bytes, matching size, and a SHA-256 checksum before storage. The renderer also safety-parses HTML, opens the PDF and extracts its text, and opens the DOCX while rejecting macros, embedded/OLE content, and external relationships; each format must contain the expected candidate name. Document tests perform broader expected-section and text checks. Production downloads recheck stored size and checksum rather than re-parsing an artifact that already passed generation-time validation. The existing `MINIO_BUCKET` is used as private storage. Do not add an anonymous-read bucket policy. Back up PostgreSQL and MinIO together so artifact metadata and objects remain consistent.

For local MinIO:

1. Set `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, and `MINIO_BUCKET` in `.env`.
2. Start `postgres` and `minio`, or the complete Compose stack.
3. Open `http://localhost:9001` only for local administration. The application itself uses the internal `http://minio:9000` endpoint in Compose.
4. Generate a package through the authenticated backend and download it through the application endpoint. Do not copy an object-storage URL into the browser.

## Configuration

AI content generation and n8n automation are disabled by default. In `local` and `test`, disabling the provider selects the deterministic verified-fact generator used by offline tests and local smoke flows; it is a safety-oriented fallback, not a claim of model-equivalent tailoring quality. Secrets remain backend/n8n environment or credential values and never belong in React, workflow exports, or version control.

| Variable | Purpose | Example/default |
| --- | --- | --- |
| `APPLICATION_PACKAGE_AUTOMATION_ENABLED` | Allows eligible n8n-triggered package requests | `false` |
| `OPENAI_CONTENT_GENERATION_ENABLED` | Enables provider-backed planning/writing | `false` |
| `OPENAI_CONTENT_GENERATION_MODEL` | Content-generation model | `gpt-5.6-terra` |
| `OPENAI_CONTENT_GENERATION_REASONING_EFFORT` | Provider reasoning effort | `low` |
| `OPENAI_CONTENT_GENERATION_TIMEOUT` | One provider request timeout | `30s` |
| `OPENAI_CONTENT_GENERATION_MAX_RETRIES` | Transient provider retry count; never unbounded | `1` |
| `CONTENT_GENERATION_DAILY_LIMIT` | Persisted daily revision ceiling for the current single-user deployment | `20` |
| `CONTENT_GENERATION_MAX_INPUT_TOKENS` | Input-token guard | `24000` |
| `CONTENT_GENERATION_MAX_OUTPUT_TOKENS` | Output-token guard | `8000` |
| `CONTENT_GENERATION_MAX_INPUT_CHARACTERS` | Pre-provider character guard | `60000` |
| `CONTENT_GENERATION_PROMPT_VERSION` | Immutable planning/writing prompt resource version | `v1` |
| `CONTENT_GENERATION_SCHEMA_VERSION` | Strict output-schema version | `v1` |
| `RESUME_TEMPLATE_VERSION` | Deterministic renderer/template version | `ats-single-column-v1` |
| `OPENAI_API_KEY` | Shared backend-only provider credential | empty; required only when provider generation is enabled |

Changing a prompt, schema, model, template, or relevant setting changes idempotency/cache identity and may make an older package stale. Versioned resources must not be edited in place after use.

## Local development

Copy `.env.example` to `.env`, replace all example credentials, and leave generation/automation disabled for the default mocked test path. To run the complete stack:

```bash
docker compose up -d --build
docker compose ps
```

To run services during backend/frontend development:

```bash
docker compose up -d postgres minio
cd backend
./mvnw spring-boot:run
```

Do not include `n8n` in that command: its Compose dependency starts the Compose backend and would compete with the Maven backend for port `8080`. Use the complete Compose stack when validating the Phase 5 n8n workflow. On Windows use `mvnw.cmd`. In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `/application-packages` after signing in. Every package page displays a persistent **Draft — not approved or submitted** notice. Provider-backed generation additionally requires an account-authorized key and explicit `OPENAI_CONTENT_GENERATION_ENABLED=true`; default tests never require a real key.

## n8n automation

Import [phase-5-eligible-application-package.json](../automation/n8n-workflows/phase-5-eligible-application-package.json) and follow the [Phase 5 setup guide](../automation/n8n-workflows/phase-5-application-package-setup.md). The workflow receives only evaluation/job UUIDs, fetches the authoritative evaluation, accepts only a completed, current `APPLY`/`STRONG_APPLY` recommendation, and sends that `evaluationId` to the generation endpoint with a stable `Idempotency-Key` and `X-N8N-Automation: true`. The authenticated marker makes the backend apply automation enablement, recommendation, current-source, and daily-limit policy. The workflow makes at most three POST attempts and retries only transport/backend `5xx` failures. Authentication, validation, quota, policy, and other `4xx` failures stop immediately. Completed success, error, and manual execution payload saving is disabled so the full package response is not retained in n8n.

The workflow neither produces documents itself nor performs an external application action. Package generation, policy checks, idempotency, state transitions, and artifact persistence remain backend responsibilities.

## Verification and synthetic evaluation data

Default verification does not use a live provider:

```bash
cd backend
./mvnw clean verify
cd ../frontend
npm ci
npm run lint
npm run test -- --run
npm run build
cd ..
docker compose config
```

The privacy-safe [application-generation dataset](../test-data/application-generation/phase-5-evaluation-cases.json) covers a strong backend match, unsupported technology, prompt injection, verified and missing metrics, deterministic location, compensation, subjective motivation, and sensitive demographic questions. It contains fictional organizations, reserved domains, and no real candidate data.

## Troubleshooting

- **Generation returns `401`:** sign in again or correct the n8n HTTP Basic Auth credential. Do not place credentials in the workflow JSON.
- **Generation returns `403`:** confirm authentication/authorization and ownership. Manual override reasons do not bypass ownership, fact, sensitive-question, or validation rules.
- **Generation returns `409`:** reuse the original idempotency key and inspect the returned/current package. Reload before retrying an optimistic-lock edit.
- **Generation returns `400`:** inspect the safe problem detail. Phase 5 eligibility, immutable-source, automation-enable, daily-quota, question-policy, and generation-validation failures currently use this status. Correct the source or configuration and do not weaken evidence validation or retry policy failures rapidly.
- **Provider is disabled or credentials are missing:** keep the provider off for tests, or set the backend-only key and explicitly enable content generation. Never put `OPENAI_API_KEY` in Vite or n8n.
- **A revision fails after one repair:** inspect its sanitized failure code. The bounded repair is intentionally not repeated; correct the source facts, prompt/schema, or provider problem and create an explicit new revision.
- **A package is stale:** review the stated source/version reason and regenerate. Staleness never mutates the historical revision.
- **Artifact download returns `404`/`403`:** verify the package owner and that rendering completed. Do not look up or expose the MinIO key directly.
- **Artifact download returns `503`:** confirm MinIO health and credentials, then reconcile metadata and objects. Do not make the bucket public as a workaround.
- **n8n stops on a `4xx`:** this is intentional for authentication, validation, quota, idempotency/policy, and authorization responses. Fix the input or configuration; do not add an infinite retry loop.
- **Compose cannot bind n8n port `5678`:** leave the existing instance untouched and set `N8N_PORT` in `.env` to a free host port such as `5679`, then recreate only the project n8n service.

## Phase 6 boundary

Formal review/approval, reviewer assignment, external form completion, browser automation, recruiter/email sending, and application submission remain follow-up work. No Phase 5 API or `READY` state authorizes those actions.

Current operational constraints are intentionally visible: the temporary Phase 1 Basic-auth deployment is single-user; package generation is a bounded inline backend operation even though it returns an accepted package resource; the offline deterministic generator favors provenance over prose quality; and account-specific access to `gpt-5.6-terra` is not proven by default tests. A production deployment must use HTTPS, replace example credentials, verify its provider/model access explicitly, retain private object-storage policy, and design multi-user identity/quota isolation before expanding beyond the current ownership model.
