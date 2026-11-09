# 04 — Tracer: a cited explanation for a settled payment

Status: ready-for-agent
Category: enhancement

## What to build

The tracer bullet of `.scratch/copilot/PRD.md`: the whole vertical slice for the simplest subject, a settled outbound FPS payment.

**The module.** `malai-services/copilot-service/`, registered in the root `pom.xml` (`<modules>` *and* `<dependencyManagement>`) and wired into `malai-api` by dependency plus configuration import. Owns no tables. Shaped like `exec-service`: per-source components fanning out through existing clients with a `CopilotServiceTokenClient`. **Deliberately no `malai-copilot-client` module** (ADR-0012) — record that absence in the service's ArchUnit test so nobody adds the "missing" pair later.

**The route.** `POST /api/v1/admin/copilot/explanations`, ADMIN-only, idempotency-protected (register the path in the service's `IdempotencyProtectedRoutes` bean). Body: `{ subjectType: "PAYMENT", subjectId }`. Response: `{ decisionId, outcome, claims: [ { text, sources: [ { type, id } ] } ] }`. In `openapi.yaml` first, per the house rule, with the non-authoritative wording in the description.

**The Timeline.** For a payment subject: the payment row (issue 01), its audit events (issue 02, `resourceType = "Payment"`), the ledger entries of its transaction and its hold (issue 01), and the originating standing order or mandate where there is one. Ordered by moment. Redaction follows screening — account numbers and IBANs masked to last four — but balances and holds are included.

**The seam.** `CopilotDecider` with `modelId`/`modelVersion` and `explain(timeline, renderedPrompt)`, plus one deterministic implementation named honestly (`malai-copilot-template` v1) which turns Timeline records into Claims, each citing the record it was generated from. A real `copilot-payment/v1` template resource with a genuine SHA-256 over its bytes.

**Grounding.** Every claim's source refs are validated against the assembled Timeline. In this slice, the happy path only — the refusal path is issue 05.

**Recording.** One Model Decision appended *before* the response is served: correlation `PAYMENT`/subject id, `advisory = true`, one retrieved-context ref per Source record with a content hash over the projection actually used, verdict = the claims, latency measured, `costMicros = 0`, honest model naming.

Configuration blast radius: the payments, ledger and MDR base-url properties must be declared in copilot-service and `malai-api` across main, test and acceptance-boot configuration.

## Acceptance criteria

- [ ] Requesting an explanation for a settled FPS payment returns claims that cite the payment, its audit events and its ledger entries, pinned by a happy acceptance test with `noExtraProps()` at every level
- [ ] Exactly one Model Decision is appended before the response, with the recorded request body pinned: honest model id/version, real template hash, `advisory = true`, `PAYMENT` correlation, one retrieved-context ref per Source record
- [ ] Every source id in every returned claim appears in that entry's retrieved-context refs — asserted, not assumed
- [ ] The rendered prompt masks account numbers and IBANs to last four and includes balance and hold figures
- [ ] A MANAGER JWT receives 403; an unknown payment id returns 404; a missing `Idempotency-Key` is rejected and a replayed key returns the first explanation
- [ ] No `malai-copilot-client` module exists, and the copilot ArchUnit test records that as deliberate
- [ ] The copilot acceptance diagram shows the payments, ledger and MDR lanes
- [ ] `mvn install` green

## Blocked by

- 01 (`01-internal-reads-the-timeline-needs.md`)
- 02 (`02-audit-reader-in-malai-security.md`)
- 03 (`03-mdr-entries-declare-advisory.md`)
