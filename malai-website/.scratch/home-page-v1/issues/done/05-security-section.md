# 05 — Security section: trust ledger

Status: ready-for-agent

## Parent

`.scratch/home-page-v1/PRD.md`

## What to build

The second below-the-hero section, retiring the security objections a bank technologist or CISO will raise on a first read. The section is a vertical _trust ledger_ — a list of standards-language claims, each with its evidence rendered immediately beside it. Format mirrors how CISOs scan vendor security pages: scannable, claim + evidence per row, no marketing fluff.

Initial trust-ledger entries:

- **TLS at the edge** — HTTPS-only origins; modern cipher suites.
- **Authentication** — Short-lived JWT bearer tokens, signed with a managed key, validated on every request. The customer-facing app never holds a long-lived credential.
- **Authorization** — RBAC enforced at the API, with module fences enforced at build time.
- **Encryption at rest** — Database, object storage, and image registry all use managed keys.
- **Append-only audit** — Hash-chained, tamper-evident.
- **Secrets management** — Managed secret store; per-task identity; zero plaintext.
- **Inter-service authentication** — OAuth2 between services.
- **Idempotency** — `Idempotency-Key` on synchronous and asynchronous writes; 24-hour replay-safe mutations.

Each claim ships with an _evidence_ slot in the content schema. v1 evidence may be a brief factual one-liner (e.g. "validated per request, no caching"), a link to a relevant standard, or — where placeholder is acceptable — a marker that real verifiable evidence (screenshot, SSL Labs link, sanitised audit row) lands in a later evidence-focused issue. The schema must accommodate richer evidence types later without breaking; do not hard-code "string only."

The section mounts under the `#security` anchor for `StickyNav` from Issue 03.

Content lives in the `content` module. No section copy is inlined into the component.

Forbidden terms must not appear in claims, evidence text, or any auxiliary copy. The brand on the section is _Malai_.

The page-smoke spec extends to assert the section is present, each trust-ledger entry from the content collection appears in the rendered DOM with its claim and evidence slot, and the section `id` matches.

No client-side JS may be added by this section.

## Acceptance criteria

- [ ] A `SecuritySection` Astro component exists and is mounted in `index.astro` under an element with `id="security"`.
- [ ] The `content` module gains a typed `trustLedger` collection — each entry has `{claim, evidence}` where `evidence` is a union shape that admits a string today and richer types (link, image reference) later without schema migration.
- [ ] The section renders a vertical list of claim/evidence rows for the initial eight entries.
- [ ] Page-smoke spec extends to cover section presence and trust-ledger content.
- [ ] No client-side JS hydrated by this section.
- [ ] No forbidden term appears in rendered HTML for this section.
- [ ] Lighthouse performance and accessibility do not regress relative to the post–Issue 01 baseline.

## Blocked by

- `01-walking-skeleton.md` — needs the layout, content module, and page composition.
