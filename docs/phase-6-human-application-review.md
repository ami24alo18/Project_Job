# Phase 6 — human application review and approval gate

Phase 6 lets the package owner inspect and decide one exact Phase 5 revision. It does not submit applications, fill forms, log in to job boards, send messages, or invoke AI.

## Review states

| From | Decision/event | To |
| --- | --- | --- |
| `NOT_READY` | generation and validation succeed | `PENDING_REVIEW` |
| `PENDING_REVIEW` | all server checks and checklist pass | `APPROVED_FOR_HANDOFF` |
| `PENDING_REVIEW` | reviewer requests changes | `CHANGES_REQUESTED` |
| Any reviewable state | reviewer rejects | `REJECTED` |
| `APPROVED_FOR_HANDOFF` | revision/source/artifact/validation changes | `INVALIDATED` |
| regeneration | new immutable revision | new revision is `PENDING_REVIEW`; old history remains |

Review decisions are append-only. An invalidated approval remains in history with its timestamp and reason.

## Reviewer workflow

1. Open `/application-review` and filter the server-paginated queue.
2. Open a revision and inspect its exact source versions, content, claim evidence, warnings, unsupported requirements, application questions, preview, and artifact metadata/downloads.
3. Resolve each `USER_INPUT_REQUIRED` question as acknowledged, completed, or intentionally excluded. Sensitive questions remain unanswered automatically.
4. Confirm every checklist item. Approve, request changes, or reject. Change/reject require a comment.
5. Use the existing package page for controlled edits or regeneration. Either action prevents the prior approval from being handed off.

The approval confirmation states: “Approval makes this exact revision eligible for Phase 7 handoff. It does not submit an application.”

## API

| Method | Path |
| --- | --- |
| `GET` | `/api/v1/application-packages/review-queue` |
| `GET` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}/review` |
| `GET` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}/handoff-eligibility` |
| `POST` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}/review/approve` |
| `POST` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}/review/request-changes` |
| `POST` | `/api/v1/application-packages/{packageId}/revisions/{revisionId}/review/reject` |

Decision requests contain `reviewRecordVersion`, `checklist`, optional `comment`, and a `questionResolutions` map. Stale versions return the existing conflict response. Queue filters include `reviewStatus`, `recommendation`, `company`, `stale`, `from`, `to`, `page`, and `size`.

## Invalidation and Phase 7 contract

Phase 7 must call `HandoffEligibilityService.check(packageId, revisionId)` immediately before any external action and require `eligible=true`. The service requires current ownership, `APPROVED_FOR_HANDOFF`, a current non-stale `READY` revision, matching source checksums, valid candidate provenance, sensitive-question compliance, an unchanged validation checksum, and readable HTML/PDF/DOCX artifacts matching the approved manifest. Phase 7 must not copy this logic or rely on a UI/status value.

## Local verification

```powershell
cd backend
mvn "-Dmaven.repo.local=C:\Users\yadav\Project_Job\.m2-cache" test

cd ..\frontend
npm.cmd run test -- --run
npm.cmd run build
```

Run the existing Compose stack as documented in the README, sign in as the configured owner, generate a Phase 5 package, then open `http://localhost:5173/application-review`. PostgreSQL migration `V8` is applied automatically. MinIO remains private; previews and downloads flow through authenticated backend endpoints.
