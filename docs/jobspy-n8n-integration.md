# JobSpy and n8n integration

JobSpy is an optional acquisition provider. It does not replace the existing JSearch workflow, the external-event API, or n8n scheduling. The private Python worker calls JobSpy, n8n maps the bounded result into an authenticated event, and Spring remains the system of record for normalization, deduplication, provenance, run history, and matching.

```text
n8n schedule
  -> private JobSpy worker POST /v1/scrape
  -> n8n validates and maps the result
  -> Spring POST /api/v1/job-sources/{sourceId}/external-events
  -> existing normalization, deduplication, jobs UI, and matching
```

The worker has no database, MinIO, Docker-socket, or public host port. It accepts no arbitrary URL, proxy, cookie, browser session, login, header, or executable selector. The deployment-owned `JOBSPY_ALLOWED_SITES` allowlist is empty by default, so no job board is contacted until an operator explicitly approves one.

## License and usage policy

The pinned `python-jobspy==1.1.82` package is distributed under the MIT license; see `jobspy-worker/THIRD_PARTY_NOTICES.md`. That software license does not grant permission to collect data from a job board. Each operator remains responsible for the board's current terms, robots policy, local law, data minimization, and an appropriate request rate.

Do not use this integration to bypass a login, CAPTCHA, access control, `robots.txt`, rate limit, `403`, or `429`. Do not add rotating/residential proxies or authenticated browser state. Disable a board immediately when permission is uncertain or the provider begins rejecting requests. The application intentionally does not enable any site by default.

## Configure and run

1. Copy `.env.example` to `.env` and set `JOBSPY_WORKER_TOKEN` to a random value of at least 24 characters.
2. After reviewing a board's current terms, set an explicit comma-separated allowlist, for example `JOBSPY_ALLOWED_SITES=indeed,google`. Supported identifiers are `indeed`, `linkedin`, `zip_recruiter`, `glassdoor`, `google`, `bayt`, `naukri`, and `bdjobs`; availability still depends on JobSpy and the board.
3. Build and start the stack:

   ```bash
   docker compose up -d --build
   docker compose ps
   ```

4. Sign in, open `/job-sources`, and create an external source with connector **JobSpy (n8n worker)**. Copy the one-time source token; only its SHA-256 hash is stored.
5. Import `n8n/workflows/jobspy-to-job-agent.json` into n8n.
6. Create two n8n Header Auth credentials:
   - worker credential: header `X-JobSpy-Worker-Token`, value `JOBSPY_WORKER_TOKEN`;
   - backend credential: header `X-Job-Agent-Webhook-Token`, value from step 4.
7. Attach the credentials to the two HTTP Request nodes. In **Configure JobSpy Source**, replace the source ID, choose only allowlisted sites, and set the query, location, country, age, and result bound. Keep the backend base URL internal (`http://backend:8080`) and the worker URL internal (`http://jobspy-worker:8090`) in Compose.
8. Run the workflow manually once. Confirm the JobSpy source detail page shows a `WEBHOOK`/`PUSH_BATCH` run and that each job displays `JOBSPY` plus its origin publisher. Then enable the schedule.

If n8n is an independently managed container rather than the `compose.yaml` service, connect it to the Compose `job-agent` network before using the internal service names. Do not publish the worker port merely to work around container networking.

## Runtime behavior

- `GET /healthz` is an unauthenticated container health check and exposes no secret.
- `POST /v1/scrape` requires the worker token, rejects unknown fields and unapproved sites, bounds input/output/concurrency, disables proxies, and terminates work at the configured hard timeout.
- The worker maps each result to the existing `ExternalJobRecord` contract and removes common tracking query parameters. It returns neither raw HTTP responses nor complete request diagnostics.
- n8n sends no event when JobSpy returns zero records. Successful batches use the backend's source token and matching `Idempotency-Key`/`eventId`.
- `400` means invalid bounded input, `403` invalid worker authorization, `429` worker capacity exhausted, `502` a safe provider failure, and `504` a hard timeout. These failures stop that execution; the workflow does not add an outer retry loop.
- Push batches never claim a complete provider inventory, so an absent result cannot mark an existing job as removed.

Use `n8n/workflows/jsearch-to-job-agent.json` for JSearch and `n8n/workflows/jobspy-to-job-agent.json` for JobSpy. Both terminate at the same Spring event contract, so a future scraper API can be introduced as another connector without changing job storage or matching.
