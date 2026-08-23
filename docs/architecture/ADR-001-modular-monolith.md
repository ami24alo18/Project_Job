# ADR-001: Modular monolith

- Status: Accepted
- Date: 2026-08-21

## Context

The Job Application Agent will eventually coordinate related profile, job, document, approval, and tracking workflows. Phase 1 needs dependable deployment and explicit boundaries, but it does not have independently scaling workloads or teams that justify distributed operations.

## Decision

Build one Spring Boot application with packages representing business modules. A module owns its future domain model and exposes behavior through explicit interfaces at its boundary. Cross-module access should use those interfaces rather than another module's persistence details. Shared code belongs in `common` only when it is genuinely cross-cutting; operational foundation code belongs in `system`.

The React client, PostgreSQL, MinIO, and n8n remain separate runtime components because they serve distinct platform roles, not because the application domain is split into microservices.

## Consequences

Deployment, transactions, local development, testing, and refactoring stay simple. Package coupling must be reviewed as features arrive because process boundaries do not enforce it. Kafka, queues, service discovery, distributed tracing, and Kubernetes are deferred.

## Future extraction criteria

A module may become a service only when evidence shows an independent scaling or availability requirement, a strong security/isolation need, a separate ownership/release cadence, or a workload that cannot operate reliably inside the application process. Extraction must include a defined API, data ownership, failure semantics, and operational capacity.
