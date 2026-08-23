# Phase 4 — deterministic filtering and AI job matching

## Implemented foundation

V4 creates matching configuration, deterministic rule, evaluation, requirement, LLM execution, run, and feedback storage. The backend provides authenticated matching-settings and per-job evaluation/history endpoints, stable rule reasons with boundary-safe exclusions, immutable-input cache keys, prompt/schema checksums, normalized server scoring, and feedback persistence. React routes cover settings, evaluations, runs, and quality. AI remains off by default and hard-failed jobs never need it.

Prompt and schema are `job-evaluation/v1`; the configured default model is `gpt-5.6-terra` with low reasoning effort. Official documentation confirms the Responses endpoint and Structured Outputs support, but account-specific model access can only be established by an opt-in live test.

## Verification

Run `./mvnw clean verify` from `backend` (`.\mvnw.cmd clean verify` on Windows). The verified Phase 4 delivery migrated PostgreSQL cleanly through V1–V4. The first post-change run executed 106 tests; only two Phase 3 assertions expecting schema version 3 failed and were updated to version 4. No live OpenAI call ran because no explicit live-test opt-in/key was supplied.

## Known remaining work

Batch `evaluate-ready` orchestration, durable run polling/recovery, completed AI output validation/persistence, daily budget reservation, quality-summary calculations, enriched job list/detail views, synthetic 30–50 case fixtures, and a Phase 4 evaluate-ready n8n workflow remained outside the Phase 4 delivery. Phase 5 resume and application-content generation is a separate bounded capability; see the [Phase 5 guide](phase-5-verified-application-packages.md) and its import-validated n8n workflow rather than treating it as Phase 4 behavior.
