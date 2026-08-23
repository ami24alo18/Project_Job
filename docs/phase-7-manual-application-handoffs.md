# Phase 7 — approved manual application handoffs

Create a handoff only from a Phase 6-approved revision. The server rechecks `HandoffEligibilityService`, active job state, canonical HTTP(S) apply URL, deadline, sources, and artifact manifest. It never contacts the application site.

| State | Meaning |
| --- | --- |
| `READY_FOR_MANUAL_SUBMISSION` | approved kit is ready for the owner |
| `OPENED_EXTERNALLY` | owner explicitly launched the canonical URL; not submitted |
| `SUBMITTED_REPORTED_BY_USER` | owner explicitly reported manual submission |
| `NOT_SUBMITTED` / `CANCELLED` | immutable user-recorded outcome |
| `BLOCKED` / `EXPIRED` | current eligibility prevents a further handoff action |

API: create a handoff at `POST /api/v1/application-packages/{packageId}/revisions/{revisionId}/handoffs`; list/detail/kit/eligibility are under `/api/v1/application-handoffs`. State actions are `launch`, `report-submitted`, `mark-not-submitted`, and `cancel`. Creation and action calls require `Idempotency-Key` and state calls carry `recordVersion`.

Run locally with `mvn test` in `backend`, then `npm.cmd run test -- --run` and `npm.cmd run build` in `frontend`. Open `/application-handoffs/{handoffId}` after creating an approved handoff. The frontend opens only the URL returned by the launch endpoint in a new tab with `noopener,noreferrer`; it does not preload, fetch, or iframe the external site.

Phase 8 integrations must use handoff status and immutable activity only; they must not infer employer acceptance from `SUBMITTED_REPORTED_BY_USER`.
