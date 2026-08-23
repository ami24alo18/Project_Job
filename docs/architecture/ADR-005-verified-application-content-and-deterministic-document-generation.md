# ADR-005: Verified application content and deterministic document generation

- Status: Accepted
- Date: 2026-08-22

## Context

An application package contains persuasive prose, but many statements in that prose are factual candidate claims. A fluent model can accidentally invent a technology, metric, date, title, employer, qualification, or personal detail. A generated resume also needs reproducible HTML, PDF, and DOCX artifacts without giving model output control over executable templates or external resources.

Phase 5 must therefore produce useful drafts while preserving the project's human-in-the-loop boundary. It does not approve a package, submit an application, complete an external form, or send a message.

## Decision

### Bind every revision to immutable sources

Each application-package revision records the exact candidate profile version, completed Phase 4 evaluation, normalized job checksum, profile checksum, evaluation checksum, prompt version, schema version, model, generation settings, and resume-template version used to create it. Candidate facts are resolved only from that profile version and must have an eligible verification status.

A newer active profile or evaluation can make a package stale, but it never rewrites historical provenance. Regeneration creates another revision. It does not silently replace prior content or user-edited text.

### Separate planning from writing

Generation has two bounded stages:

1. The tailoring-plan stage selects eligible fact IDs, requirement IDs, experience and project entries, skill order, omissions, and unsupported requirements. It does not persist polished resume prose.
2. The writing stage receives only the validated plan and its selected sources. It returns structured content and claim atoms with evidence references.

This separation makes source selection reviewable before prose is accepted and reduces the opportunity for an unsupported job requirement to become a candidate claim.

### Require strict structured output and claim provenance

The provider is behind the existing AI abstraction and uses the Responses API with strict JSON Schema, provider-side storage disabled, and no tools, web search, file search, or external actions. Job descriptions, employer text, and application questions are delimited as untrusted data, never treated as instructions.

The backend, not the model, decides whether output is valid. Every candidate factual claim must reference eligible fact IDs from the exact profile version. Referenced requirements and job fields must also exist. Deterministic validation rejects missing evidence, wrong-version IDs, unsupported numbers, dates, organizations, titles, named skills, duplicate or conflicting claims, prohibited sensitive content, malformed output, and excessive length. At most one bounded repair attempt may address schema or provenance errors; another failure closes the revision as failed.

Job requirements are not candidate facts. Unsupported requirements remain warnings or omissions and cannot be converted into claimed skills. Unknown candidate information remains unknown.

### Keep contact and sensitive data outside model generation

The renderer injects the candidate's name, email, phone, location, and profile links from the exact profile snapshot after generated content passes validation. Contact details are not needed for tailoring decisions and are not sent to the model. Sensitive-question answers are never generated automatically, and missing compensation, authorization, relocation, availability, consent, or attestation data is never inferred.

### Render documents deterministically

Validated structured content is the source for a versioned, repository-owned, single-column ATS template. Server-side code renders the HTML preview, PDF, and DOCX; the model cannot emit HTML, CSS, DOCX XML, PDF bytes, macros, scripts, or executable templates. File names are sanitized. Before private MinIO storage, runtime checks cover type, extension, nonempty size, and checksum; they also safety-parse HTML, open and extract text from the PDF, and open the DOCX while rejecting macros, embedded/OLE content, and external relationships. Each format must contain the expected candidate name. Renderer tests assert broader expected content, while production downloads recheck size and checksum. Authenticated backend downloads enforce ownership and do not expose storage keys, MinIO credentials, public object URLs, or signed URLs.

### Preserve the Phase 5 boundary

All packages and revisions are drafts. `APPROVED` is not a Phase 5 status. Editing and content-replacement confirmation are draft-management controls, not formal approval. Review queues, reviewer assignments, browser or login automation, form filling, CAPTCHA handling, recruiter or email sending, and application submission belong to later phases and are excluded.

## Consequences

- Application-package records remain small, auditable metadata and structured content; binaries stay in private object storage.
- Idempotency and cache identity include candidate, job, evaluation, prompt, schema, model, template, and generation settings. Cross-candidate cache reuse is prohibited.
- Exact provenance and deterministic rendering cost more storage and validation work than retaining one model-authored document, but failures are explainable and historical revisions remain reproducible.
- User edits change the content origin to `USER_EDITED` and remove automatic verification until revalidation succeeds.
- A package can become stale without corrupting or deleting the revision that was originally generated.
- n8n may request eligible drafts through the authenticated backend API, with a finite retry bound. The backend remains the policy, idempotency, quota, validation, and persistence authority.
- Human judgment is still necessary. `READY` means that a draft passed Phase 5 generation and artifact checks; it does not mean approved, submitted, or suitable for automatic external use.

## Alternatives considered

- **Generate one free-form resume document:** rejected because individual claims cannot be validated or traced reliably and the output can control presentation.
- **Use the mutable active profile:** rejected because later edits would make provenance ambiguous and historical packages irreproducible.
- **Let the model generate HTML or office-document markup:** rejected because it increases template-injection, rendering, and reproducibility risk.
- **Include approval or submission in the same workflow:** rejected because generation quality and external-action authorization are separate trust boundaries.

## Related decisions

- [ADR-002: Verified resume fact bank](ADR-002-verified-resume-fact-bank.md)
- [ADR-004: Explainable AI job matching](ADR-004-explainable-ai-job-matching.md)

## OpenAI references

- [Responses API Java reference](https://developers.openai.com/api/reference/java/resources/beta/subresources/responses)
- [GPT-5.6 Terra model reference](https://developers.openai.com/api/docs/models/gpt-5.6-terra)
