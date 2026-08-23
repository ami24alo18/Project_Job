# Synthetic Phase 5 application-generation data

`phase-5-evaluation-cases.json` is a small, versioned evaluation fixture for fact-grounded application-package generation. It deliberately uses fictional organizations and projects, synthetic UUIDs, and no email address, phone number, physical address, resume binary, API key, or real person.

The fixture covers:

- completed synthetic Phase 4 evaluations, including a strong Java/Spring backend match with an exact verified `28%` metric;
- a job requiring unsupported Rust and Kubernetes skills;
- malicious job and question text attempting to override policy;
- a profile with verified experience but no numeric achievement;
- deterministic location, compensation, subjective motivation, and sensitive demographic questions;
- expected HTML, PDF, and DOCX artifact types and cross-cutting provenance assertions.

Tests may adapt the neutral fixture fields into persistence or provider DTOs, but must not weaken `globalAssertions`. Model-dependent tests should use a mock implementation and deterministic structured responses. Default test runs must not require network access or a real provider key.

When adding a case, keep it synthetic, use a new stable identifier, state the expected safety property, and avoid generated personal documents in version control.
