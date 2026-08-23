# Phase 8 — user-reported application tracking

Phase 8 extends a `SUBMITTED_REPORTED_BY_USER` Phase 7 handoff into one private tracker. Statuses and notes are user reported; this application never monitors a job board, mailbox, recruiter, or employer. Email reminders are disabled because no mail adapter is configured.

Current tracker APIs are under `/api/v1/applications`: list, detail, status update, note, archive, restore, and immutable timeline. Status transitions are server validated and use optimistic locking. Archived trackers are excluded by default.

Future Phase 8 additions required before enabling production reminders are reminder scheduling, in-app notification preferences, and scoped aggregate analytics. No external action is authorized by tracking state.
