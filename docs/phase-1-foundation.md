# Phase 1 foundation

## Implemented scope

Phase 1 establishes the Spring Boot modular-monolith shell, PostgreSQL/Flyway foundation, public operational endpoints, n8n secret validation, deny-by-default security, consistent API errors, a React dashboard health check, containers for all required platform services, CI, and operator documentation.

No candidate, job, matching, document generation, approval, submission, or tracking behavior is implemented.

## Architecture decisions

- Java 21 target with Spring Boot 3.5.x, a currently supported stable line compatible with Java 21.
- One backend deployable with documented package boundaries.
- PostgreSQL database `job_agent` for the backend and a distinct database `n8n` for n8n. The shared server role is a local deployment convenience; n8n cannot use the backend database by configuration.
- Vite uses a local `/api` proxy; production Nginx proxies `/api/` by Compose service name.
- The n8n workflow is documented but no unverified version-specific export is committed.

## Verification commands

```text
cd backend && ./mvnw clean verify
cd frontend && npm ci
cd frontend && npm run lint
cd frontend && npm run test -- --run
cd frontend && npm run build
docker compose config
docker compose up -d --build
```

## Test results

- Backend `clean verify`: passed (9 tests discovered, 8 passed, PostgreSQL Testcontainers test skipped because the Docker Desktop Linux engine pipe was absent).
- Frontend `npm ci`: passed; 352 packages installed and 0 vulnerabilities reported.
- Frontend lint: passed.
- Frontend Vitest: passed (6 tests).
- Frontend production build: passed.
- `docker compose config --quiet`: passed.
- Full Compose startup: not run because `docker info` reported that `//./pipe/dockerDesktopLinuxEngine` does not exist. Runtime service health and the actual PostgreSQL Flyway execution remain unverified in this environment.

## Known limitations and deferred work

Phase 1 has no login/authentication, user/domain data, MinIO bucket provisioning, n8n workflow export, or business automation. Local default credentials are intentionally development-only and must be replaced in `.env` before a non-local deployment.
