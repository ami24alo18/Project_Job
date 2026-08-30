# Career-site discovery and extraction threat model

- Status: Guarded discovery and built-in adapters implemented; isolated browser worker remains disabled pending deployment-specific network-policy verification
- Date: 2026-08-25
- Architecture: [ADR-008](architecture/ADR-008-unified-career-site-and-external-job-ingestion.md)

## Assets and trust boundaries

Protected assets include PostgreSQL, MinIO documents, candidate/profile data, application artifacts, n8n and provider credentials, webhook tokens, the Docker host/socket, cloud metadata, private-network services, and application availability.

Trust boundaries are:

```text
Authenticated browser
       | untrusted career URL
       v
Spring discovery client ---- public DNS/HTTPS ---- employer/ATS site

Public site ---- isolated extraction worker ---- narrow external-event ingress
                       X
             no private application network

n8n/JSearch ---- source-scoped token ---- external-event ingress
```

Career URLs, DNS answers, redirects, HTTP content, provider JSON, HTML, JavaScript, job descriptions, URLs inside jobs, n8n payloads, and publisher labels are untrusted.

## Security invariants

1. The job module never fetches a URL contained in a job.
2. Discovery never creates or enables a source.
3. A source cannot execute until a backend-known connector reports `SUPPORTED` or a reviewed external recipe is explicitly associated.
4. The Spring process never executes user-supplied selectors, JavaScript, browser profiles, cookies, credentials, or arbitrary headers.
5. The extraction worker cannot reach private networks or data stores and can call only the narrow ingestion endpoint inside the application boundary.
6. Only a proven complete inventory run may advance missing-job counters.
7. Tokens, credentials, complete remote responses, descriptions, and browser state never enter logs or audit metadata.

## Threats and required controls

| Threat | Required controls | Required verification |
| --- | --- | --- |
| Direct SSRF to localhost/private services | HTTPS only; reject literal IPs and every non-public resolved address; block private, loopback, link-local, multicast, reserved, documentation, carrier-grade NAT, and metadata ranges for IPv4 and IPv6 | Unit matrix for encoded/alternative IP forms and integration tests with local targets |
| DNS rebinding | Resolve through the guarded resolver, validate every answer, connect only to validated/pinned addresses, and do not fall back to an unvalidated resolution | Resolver-change test between validation and connection |
| Redirect escape | Disable automatic redirects; resolve, validate, and bound every hop; strip sensitive headers; reject scheme downgrade and unapproved ports | Redirect chains to private, mixed-scheme, credential, loop, and excess-hop targets |
| URL parser ambiguity | Use one URI parser/canonicalizer; reject user info, fragments, invalid IDNs, ambiguous authorities, backslashes, control characters, and non-normalized hosts | Differential and malformed URL tests |
| Oversized/compression-bomb response | Bound compressed bytes, decompressed bytes, content length, read time, markup nodes, redirects, and parsed text | Oversized, streaming, gzip-ratio, and slow-response tests |
| Port scanning and availability abuse | Permit only configured public ports, rate-limit discovery per user/host, cap concurrent requests, use short timeouts, and cache safe detection briefly | Concurrency, timeout, and rate-limit tests |
| Credential leakage | Discovery sends no cookies/auth; tokens are source-scoped and hashed; n8n/worker secrets live in credentials; redact headers and URLs with sensitive query data | Log-capture and audit assertions |
| Stored XSS/content injection | Parse HTML with the established sanitizer, persist bounded plain text, validate URLs, and render descriptions as text | Script/style/entity/control-character fixtures and frontend DOM tests |
| Malicious browser content | No browser in Spring; isolated worker uses ephemeral contexts, disables downloads/extensions, blocks nonessential resource classes, and has no private egress | Worker network-policy and malicious-page tests |
| Arbitrary code through extraction rules | Recipes are reviewed, versioned repository assets; the API accepts a recipe identifier only and never script or selectors | API unknown-field/schema tests and recipe allowlist tests |
| Webhook replay or substitution | Unique `(source,eventId)`, canonical payload checksum, exact idempotency header match, token/source binding, and conflict on changed replay | Same/different payload replay and concurrent-delivery tests |
| Cross-source spoofing | Authenticate against the path source; require configured connector to match `ingestionProvider`; do not trust payload source IDs | Token/source mismatch tests |
| Poisoned provenance | Treat publisher/provider labels as bounded unverified data; never use them for authorization | Validation and UI-label tests |
| False removal after filtered results | Persist run coverage; backend alone assigns coverage; call removal accounting only for successful complete inventory | Service and database tests for all coverage/status combinations |
| Terms or authorization mismatch | Persist support state; require explicit supported/authorized connector or reviewed recipe; keep unsupported sources disabled | Lifecycle tests preventing enable/sync |

## Guarded URL algorithm

For discovery and every built-in public-page connector:

1. Parse and normalize once.
2. Require absolute HTTPS, an ASCII/IDNA-normalized DNS hostname, no user information or fragment, and an allowed port.
3. Reject literal IPv4/IPv6 forms before DNS.
4. Resolve all A/AAAA answers with the guarded resolver.
5. Reject the target if any answer is non-public or otherwise prohibited.
6. Open a connection pinned to a validated address while preserving the validated TLS server name and Host header.
7. Validate the certificate normally.
8. Read through byte/time/decompression bounds.
9. For a redirect, close the response and restart from step 1 with the resolved location.
10. Parse only the minimum bounded evidence required for provider detection.

Proxy environment variables and the machine's default proxy configuration must not silently reroute discovery traffic. If an explicit egress proxy is introduced, it becomes part of the trusted computing base and must enforce the same destination policy.

## Extraction-worker isolation contract

The worker runs under a separate runtime identity and network policy. It may reach:

- explicitly allowlisted public career hosts and required static-resource hosts; and
- one dedicated Job Agent external-event endpoint through a constrained route.

It may not reach:

- RFC1918, loopback, link-local, carrier-grade NAT, multicast, reserved, or metadata ranges;
- PostgreSQL, MinIO, n8n management APIs, Docker APIs, or the backend's other endpoints;
- arbitrary hosts discovered from page content unless they are covered by the reviewed recipe allowlist; or
- local files, browser extensions, persistent browser profiles, or downloaded executables.

Recipes specify a canonical source host, permitted redirect/resource hosts, pagination bounds, extraction mapping, and version. A recipe update requires review and produces a new version recorded on delivered events/jobs. Failures are safe and bounded; CAPTCHA, authentication, consent bypass, or access-control circumvention is not automated.

## Operational controls

- Feature flags independently control external ingestion, career discovery, and generic extraction.
- Per-source and global delivery/fetch limits protect provider quotas and application capacity.
- Metrics expose counts, duration, safe error codes, and retry state without payloads.
- Token rotation and recipe disable actions are audited.
- The old token is invalid immediately after rotation.
- Disabling or archiving a source rejects new fetches and webhook events while preserving history.
- Provider authorization and support state are reviewed before deployment and periodically afterward.

## Release gate

Career discovery cannot be enabled until the URL, DNS, redirect, response-bound, audit-redaction, and concurrency suites pass. Generic extraction cannot be enabled until runtime network-policy tests prove that the worker cannot reach the protected private assets. A provider adapter cannot be enabled until its endpoint policy, authorization basis, fixtures, pagination/completeness rules, and safe failure behavior are documented and tested.
