# 07 — Freeze, unfreeze, report lost or stolen → replacement

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The card lifecycle in the customer's hands: freeze a card instantly (`ACTIVE → FROZEN`), unfreeze it (`FROZEN → ACTIVE`), or report it lost/stolen — which cancels it terminally (`→ CANCELLED`) and auto-issues a replacement card with a new PAN against the same account, returned in the response. A frozen or cancelled card declines every authorisation request with a visible reason (the decisioner from issue 04 already implements this — this slice makes the states reachable and proves the loop); freezing never affects already-approved authorisations or settled entries.

These are the first public card write endpoints: they take an `Idempotency-Key` per the repo invariant (surface the open critique on the shared idempotency package before adding the new uses), enforce ownership via the authenticated principal, and emit audit events per the existing write-endpoint pattern.

Domain language: Card states and invariants in `card-service` CONTEXT.md.

## Acceptance criteria

- [ ] OpenAPI edited first: freeze, unfreeze, and report-lost-or-stolen paths, additive, `oasdiff` green
- [ ] Valid transitions only: freeze from `ACTIVE`; unfreeze from `FROZEN`; report-lost from `ACTIVE` or `FROZEN`; `CANCELLED` is terminal — invalid transitions rejected 4xx with no state change, full-entity asserted
- [ ] Report-lost cancels the card and atomically issues a replacement: old card `CANCELLED`, new card `ACTIVE` with a distinct Luhn-valid PAN on the same account, one-active-card-per-account invariant preserved throughout, replacement returned in the response
- [ ] Frozen card: an authorisation request through the decision surface is declined with the frozen reason and persisted as a **Decline** — the full freeze → visible-decline → unfreeze → approved loop proven at acceptance level
- [ ] Cancelled (replaced) card declines with the cancelled reason; the replacement card's PAN authorises successfully
- [ ] Already-approved (`AUTHORISED`) holds and settled entries are untouched by freeze/cancel
- [ ] All three endpoints: ownership-verified (foreign/unknown card → not-found per convention), `Idempotency-Key` honoured (replay returns the original outcome, no double replacement), audit events emitted and asserted per the existing pattern — with the PAN redacted
- [ ] Strict Kacey response assertions with `noExtraProps()`; full-entity database assertions on every transition

## Blocked by

- `.scratch/cards/issues/04-first-authorised-purchase.md`

---

Status: done (2026-07-12)

## Completion note

The customer card-lifecycle write surface, end to end. Three new public POST endpoints on
`card-service` — `freeze`, `unfreeze`, `report-lost-or-stolen` — under `/api/v1/cards/{cardId}`,
each ownership-verified via the authenticated principal, `Idempotency-Key`-protected, and audit-
emitting. Freeze/unfreeze are strict state transitions (`ACTIVE⇄FROZEN`); report-lost cancels
terminally and atomically issues a replacement `ACTIVE` card with a new PAN on the same account,
returned in the response. The issue-04 decisioner already declines FROZEN/CANCELLED — this slice
makes those states reachable and proves the freeze→visible-decline→unfreeze→approved loop and the
replaced-card-declines / replacement-approves loop at acceptance level.

Key decisions:
- Endpoints registered with the shared `IdempotencyInterceptor` via a new
  `cardServiceIdempotencyRoutes` bean (`/api/v1/cards/[^/]+/{freeze,unfreeze,report-lost-or-stolen}`),
  mirroring account-service's path-var regex. Replay returns the cached outcome transparently, so
  "no double replacement" is enforced by the interceptor, not by controller code — proven by a
  same-key replay test asserting exactly one replacement card and one audit row.
- **Open idempotency critique surfaced (per CLAUDE.md, before adding new uses):** the idempotency
  key namespace is keyed on `(service, key)` only — it is NOT scoped to the authenticated principal.
  Two holders presenting the same key would collide (payload-mismatch 422, or a cached-response
  replay if method+URI+body also match). These endpoints are safe because the `cardId` lives in the
  URL path (part of the request hash) and a card is owned by exactly one holder, so a shared key
  across holders can never produce the same method+URI → it yields a 422, never a cross-tenant leak.
  The package-level fix (per-principal key scoping) remains the open critique; this slice relies on
  path-embedded resource ids to stay safe rather than widening the critique's blast radius.
- Invalid transitions are `409 Conflict` with a new `CARD_INVALID_STATE_TRANSITION(6993)` — freeze
  from non-ACTIVE, unfreeze from non-FROZEN, report-lost on CANCELLED (terminal). Strict, not
  idempotent no-ops (idempotency is the key mechanism's job); full-entity DB assertion proves no
  state change on rejection. Ownership failures reuse `CARD_NOT_FOUND(8993)` → 404 (unknown and
  foreign are indistinguishable, per convention).
- Report-lost reuses `CardIssuer.issue` for the replacement: `saveAndFlush(old→CANCELLED)` first, so
  the issuer's `findByAccountIdAndStatus(accountId, ACTIVE)` guard sees no active card and creates a
  fresh one — the one-active-per-account invariant (partial unique index `cards_one_active_per_account`)
  holds throughout (asserted: exactly one ACTIVE for the holder, the replacement).
- Audit: `CARD_FROZEN` / `CARD_UNFROZEN` / `CARD_REPORTED_LOST_OR_STOLEN` via the mutating-service
  `AuditWriter` port. **PAN redaction proven literally:** the report-lost afterState deliberately
  carries the replacement's raw `replacementPan`; `AuditRedactor` strips the `pan`-token key to
  `[REDACTED]` before hashing/persisting — the test asserts the persisted `after_state` contains
  `[REDACTED]` and never the raw PAN.
- AC #6 (approved holds / settled entries untouched by freeze/cancel): freeze and report-lost only
  touch the `cards` table + audit — never `card_authorisations` or the ledger. Proven by a full-
  entity assertion that a seeded `AUTHORISED` authorisation (with its holdId) is byte-identical after
  report-lost.
- No schema migration: `CardStatus` already carries ACTIVE/FROZEN/CANCELLED (V22). No client change
  (all three are card-service-local writes). CONTEXT.md already documents the states/invariants.

Test-infra note: `HashChainedAuditWriter` draws the audit `event_id` from the injected
`UuidGenerator`, so audit-writing operations consume from the shared `TestUuidGenerator` queue. The
loop test therefore asserts authorisations by their (test-controlled) scheme reference and uses
Kacey `something` for the queue-ordered `authorisationId`, rather than pinning ids the audit writer
would shift.

Files:
- OpenAPI: three additive POST paths + `CardReplacementResponse` schema (oasdiff: 0 ERR / 0 WARN /
  3 INFO endpoint-added).
- malai-commons: `MalaiCode.CARD_INVALID_STATE_TRANSITION(6993)`.
- card-service: `CardLifecycleService` (freeze/unfreeze/reportLostOrStolen + audit), new
  `CardLifecycleController`, `CardReplacementResponse` model, `cardServiceIdempotencyRoutes` bean;
  tests — happy `CardLifecycleControllerTest` (4), sad `CardLifecycleControllerTest` (5), happy
  `CardFreezeAuthorisationLoopTest` (2); `WithCardService` gains `AuditTestSupport`,
  `thereIsNoIdempotencyKeyInTheDatabase`, `theAuthorisationForSchemeReference`.

Verification (serial mvn, Postgres + Localstack + WireMock up; npm test/typecheck N/A in this
Maven/Kotlin repo): full reactor compiles + installs clean (-DskipTests, EXIT 0). Tests green —
card-service 51/0/0 (40 → 51; +11 new), malai-api 21/0/0 (aggregator boots the new controller +
idempotency route bean in one JVM; arch tests green). oasdiff breaking `--fail-on ERR` EXIT 0.

Notes for next iteration:
- Issue 13 (e2e) journey 1 unblocked: the sandbox purchase → PENDING → present → freeze →
  visible-DECLINED loop is now fully reachable; the freeze/unfreeze/report-lost endpoints are the
  webapp surface for the card-controls demo beat.
- Report-lost issues the replacement with `cardholderName = old.cardholderName` (carried forward),
  consistent with the issue-03 placeholder; the real holder-name source is still pending a
  name-bearing slice.
- The idempotency per-principal-scoping critique (surfaced above) is untouched and remains open;
  future write endpoints where the resource id is NOT in the path must not rely on the path-hash
  mitigation this slice leans on.
