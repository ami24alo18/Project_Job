# ADR-007: Manual external submission gated by immutable approval

- Status: Accepted
- Date: 2026-08-23

Phase 7 never automates a job board. Credentials, cookies, CAPTCHA handling, form filling, scraping, and external HTTP calls remain outside this system. A user opens the canonical job URL and completes the application manually.

Opening a URL is not submission. The only submitted state is `SUBMITTED_REPORTED_BY_USER`, created after an explicit user confirmation; it is not employer confirmation. Every handoff binds the exact approved revision, active review decision, source checksums, canonical URL checksum, and artifact manifest. Eligibility is rechecked at creation and before every state change so invalidation cannot be bypassed.
