# ADR-004: Explainable AI job matching

- Status: Accepted
- Date: 2026-08-22

## Decision

Run conservative deterministic exclusions before AI so clearly inactive, duplicate, expired, source-removed, or explicitly excluded jobs consume no model budget. Evaluate only against an immutable published profile snapshot and opaque verified fact IDs.

Use the OpenAI Responses API with strict Structured Outputs and no web, file, function, computer, or other tools. The job description is untrusted delimited data. Store model, prompt and schema identity and checksums so results are reproducible and cache invalidation is explicit. The backend validates fact/evidence references and calculates the weighted score; an overall score supplied by a model is never accepted.

Cache identical successful inputs and enforce persisted request/token budgets before remote calls. Human review remains mandatory because matching is advisory, model output can be incomplete, and a small labelled dataset cannot establish statistical significance.

## Consequences

Provider code stays in `ai`; orchestration and persistence stay in `matching`; `job` and `profile` expose read-only provider boundaries. Prompt/schema or configuration changes make older evaluations stale rather than deleting them. No candidate contact data, resume binary/raw text, authentication data, unrelated answers, or unverified facts may enter a model request.

## References

- https://developers.openai.com/api/reference/java/resources/beta/subresources/responses
- https://developers.openai.com/api/docs/models/gpt-5.6-terra
- https://github.com/openai/openai-java
