# Phase 2 — Candidate profile and verified resume fact bank

## Implemented scope

Phase 2 implements the complete workflow from authenticated profile creation through immutable snapshot review:

```text
Profile → preferences → private master resume → draft/imported facts
        → explicit fact verification → reusable answers → published version
```

No job ingestion, scraping, LLM call, embedding, matching, tailored resume, cover letter, application draft, approval, submission, recruiter email, or analytics functionality was added.

## Domain model

- `CandidateProfile` is the single active profile, represented with a UUID for clean future references.
- `SearchPreference` is one-to-one with the profile and uses normalized collection tables for titles, locations, skills, companies, and keywords.
- `ResumeFact` is a separate lifecycle aggregate. New/manual/imported facts are draft; verification, rejection, restore, and archival are explicit transitions. Editing a verified fact resets verification.
- `ReusableAnswer` normalizes its question for uniqueness, separates lifecycle state from sensitivity, and prevents unsafe classifications for compensation, demographic, and legal data.
- `ResumeDocument` stores metadata and extracted text in PostgreSQL and a private binary in MinIO. A partial unique index and transactional activation enforce one active master resume.
- `CandidateProfileVersion` stores canonical JSONB and a checksum. Content and provenance columns are non-updatable and no update/delete API exists.
- `AuditEvent` records safe actor/action/aggregate metadata for important transitions.

Mutable aggregates use UUID identifiers, `Instant` timestamps, and optimistic `@Version` columns. Controllers return records rather than entities and delegate use cases to application services.

## API summary

All paths below require Basic authentication except the preserved Phase 1 public endpoints.

| Area | Endpoints |
| --- | --- |
| Authentication | `GET /api/v1/auth/check` |
| Profile | `GET, PUT /api/v1/profile` |
| Preferences | `GET, PUT /api/v1/profile/preferences` |
| Facts | `POST, GET /api/v1/resume-facts`; `GET, PUT /api/v1/resume-facts/{id}`; verify/reject/restore/archive actions; `POST /api/v1/resume-facts/import` |
| Answers | `POST, GET /api/v1/reusable-answers`; `GET, PUT /api/v1/reusable-answers/{id}`; verify/archive/restore actions |
| Documents | multipart upload/list/get/download/extracted-text plus activate/archive under `/api/v1/resume-documents` |
| Versions | `POST /api/v1/profile/publish`; list/get/active under `/api/v1/profile/versions` |

Fact lists support bounded pagination, stable sorting, status/category filters, tag filters, and case-insensitive search. Answer lists support category, sensitivity, and status filters. Errors use RFC 7807 problem details with field errors where applicable and the documented `400`, `401`, `403`, `404`, `409`, `413`, `415`, `422`, `500`, and `503` semantics.

OpenAPI exposes generated schemas, fictional examples, and a `basicAuth` security scheme at `/v3/api-docs` and `/swagger-ui.html`.

## Database migration

`V2__candidate_profile_and_verified_fact_bank.sql` is the only new Flyway migration. V1 was not modified. It creates the profile, preference collection, resume document, fact/tag, reusable answer, profile version, and audit tables with foreign keys, checks, filter indexes, optimistic versions, JSONB snapshots, normalized-question uniqueness, active-master/profile-version partial uniqueness, and per-profile checksum rules.

Two disposable PostgreSQL Testcontainer scenarios cover a clean migration through V2 and a V1-targeted database upgraded to V2. They are explicitly skipped when Docker is unavailable.

## Documents and extraction

The MinIO adapter implements an object-storage boundary. Upload reads only a configured-size PDF/DOCX into memory, checks Tika's detected media type against its extension, sanitizes the name, computes SHA-256, stores under a UUID-bearing key, and extracts at most two million text characters. Control characters are normalized. Extraction errors store a generic state while retaining the original object. Normal archive operations retain the object and metadata.

The bucket has no public policy and the API returns downloads only through its authenticated controller. Object credentials, endpoint, internal storage key, full document text, and request bodies are absent from logs and audit metadata.

## Security decisions

Temporary single-user Basic credentials come only from `APP_SECURITY_USERNAME` and `APP_SECURITY_PASSWORD`. Candidate-data routes use a stateless authenticated filter chain. The public Phase 1 health, n8n-secret, Swagger, and OpenAPI routes retain their behavior; unknown routes remain denied by default. The frontend keeps the Basic header only in module memory, clears it on logout/`401`, and never uses browser storage.

Production must terminate HTTPS before the frontend/backend. JWTs, registration, reset flows, and multi-user authorization are intentionally deferred.

## Initial baseline

- No `AGENTS.md` was present. README, Phase 1 report, ADR-001, migrations, security, MinIO, Compose, backend modules, and frontend routes/client were inspected.
- The workspace is not a Git working tree, so `git status` reported that fact; unrelated `.vscode` content was left untouched.
- Existing frontend tests: 6 passed.
- Existing backend non-clean `verify`: 8 passed and the PostgreSQL Testcontainer test skipped because Docker was unavailable.
- The exact clean backend command initially could not delete a Java-language-server-locked `target/classes` directory. After the generated target was verified and the locking VS Code Java language server was stopped (it restarted automatically), the final clean verification succeeded.

## Commands and results

Commands used during implementation and final verification include:

```bash
./mvnw -Dmaven.repo.local=../.m2-cache test-compile
./mvnw -Dmaven.repo.local=../.m2-cache verify
./mvnw clean verify
npm ci
npm run lint
npm run test -- --run
npm run build
docker compose config
docker info
```

The final exact backend `mvnw.cmd clean verify` completed successfully with 24 tests, zero failures, zero errors, and zero skips, then produced the executable Spring Boot JAR. This includes clean PostgreSQL migration and V1-to-V2 upgrade tests plus a real MinIO store/load integration test. Frontend verification completed with 18 tests passing, ESLint passing, and a successful production build. Vite emitted only its advisory that the main minified chunk is larger than 500 kB.

## Runtime verification

Compose wiring supplies the application credentials and upload limit to the backend and waits for healthy PostgreSQL and MinIO services. `docker compose config` validates the resolved topology. Docker became available during final verification: Testcontainers exercised a real PostgreSQL 17 migration from clean and V1 states and a real MinIO bucket/object store/load cycle, with no skipped tests. A separate live Compose restart/persistence/private-HTTP exercise was not run because the managed approval session expired before existing Docker volumes could be inspected safely; no existing volume was changed or removed.

## Known limitations

- Basic authentication is a temporary single-user mechanism; refresh requires login and external HTTPS termination is mandatory in production.
- Text extraction is synchronous. A maximum upload size and bounded text handler limit resource use, but later high-volume work should move extraction to a controlled worker.
- A successful MinIO write followed by a database failure can leave an unreachable private object; a future maintenance reconciler can identify such objects.
- The frontend currently ships one relatively large JavaScript chunk; route-level code splitting is a safe performance follow-up.
- Full-stack Compose restart/persistence and anonymous bucket-access probes remain an environment verification task. The lower-level PostgreSQL and MinIO integrations are covered with real disposable containers.

## Deferred Phase 3 work

Phase 3 may add job sources and matching only after defining its threat model and must read the active published candidate version. AI-based fact extraction, LLM use, tailored material, and application automation remain unimplemented.
