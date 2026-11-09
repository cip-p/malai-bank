# 02 — AML contract, module bootstrap, and the Gate-governed-cases ADR

Status: ready-for-agent
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The whole AML admin contract, published first (the exec-metrics precedent): case worklist read, case detail read, request-SAR-draft, close-as-dismissed, and close-as-reported — ADMIN role, exact paths and schemas in the OpenAPI contract before any controller exists. Controllers arrive in later slices and must match.

The `aml-service` module bootstrapped: registered in the root build (modules and dependency management), wired into the `malai-api` aggregator with its configuration imported, carrying its own architecture test. A new Flyway migration creates `aml_cases`: case uid, subject account, status machine `OPEN → INVESTIGATING → CLOSED_DISMISSED | CLOSED_REPORTED`, linked alert decision ids and SAR decision id as opaque refs (never foreign keys into MDR entries), timestamps.

The context documented: an aml-service CONTEXT.md defining Alert, Case, Typology, Evidence set, Sweep, SAR draft, and Tipping-off; a CONTEXT-MAP entry. A new ADR records that an AML case's terminal transitions are Gate-governed — extending "referred payments move only via the Gate" beyond payments.

## Acceptance criteria

- [ ] OpenAPI contract carries all five admin AML operations with request/response schemas; `oasdiff` against main passes (additive only)
- [ ] `aml-service` builds inside `mvn install` and boots inside the aggregator
- [ ] `aml_cases` migration applies cleanly; status machine and opaque-ref shape as decided
- [ ] Architecture test present and green
- [ ] CONTEXT.md, CONTEXT-MAP entry, and the Gate-governed-cases ADR published
- [ ] `mvn install` green

## Blocked by

None - can start immediately
