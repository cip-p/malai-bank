# 11 — Sandbox card triggers: purchase, present now, refund

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The demonstrator's card controls, on the authenticated Sandbox surface (backend ADR-0003 / the sandbox pattern): trigger a card purchase by choosing one of the active holder's cards, a merchant from the stub's fictional directory, and an amount; force pending captures to present now; and trigger a merchant refund against a settled purchase. A merchant read surface exposes the stub's card-merchant directory for the webapp's picker, parallel to the existing counterparties read.

sandbox-service resolves the chosen card (ownership-verified via the card client — the PAN never comes from the webapp) and calls the stub's demo endpoints through the stub client; the stub then originates the real flow through gateway and card-service, so a sandbox-triggered purchase is indistinguishable from an ambient one. Declines need no special trigger: freeze the card or exceed the limit and trigger a normal purchase.

## Acceptance criteria

- [ ] OpenAPI edited first: card-purchase, present-now, and refund trigger paths plus the merchant read, additive, `oasdiff` green
- [ ] All triggers holder-scoped via the authenticated principal; the card is resolved with ownership verification (a foreign or unknown card → not-found per convention) and its PAN obtained server-side via the card client — never accepted from the request
- [ ] Purchase trigger: outgoing stub demo-origination call asserted (`madeTheHttpRequest` with PAN, merchant, amount); the response relays approved/declined so the demonstrator sees the outcome
- [ ] Present-now trigger: outgoing stub call asserted; acceptance proves a pending purchase settles as a result (composed with the presentment path)
- [ ] Refund trigger: references a settled purchase; outgoing stub call asserted; unknown/unsettled reference rejected 4xx
- [ ] Merchant read returns the stub's card-merchant directory (outgoing stub call asserted), strict Kacey with `noExtraProps()`
- [ ] `malai-stub-client` gains the four operations with WireMock-backed request-construction + deserialisation tests
- [ ] Kensa acceptance happy/sad suites in the existing sandbox-service style

## Blocked by

- `.scratch/cards/issues/05-presentment-settles-the-purchase.md`
- `.scratch/cards/issues/10-reversal-and-merchant-refund.md`

---

## Done (2026-07-12)

Shipped the demonstrator's authenticated Sandbox card surface: a card-merchant
read plus three triggers (purchase, present-now, refund). sandbox-service
resolves the chosen card server-side and relays to the stub scheme, so a
sandbox-triggered purchase/refund is indistinguishable from an ambient one.

Key decisions:
- **PAN + settled-reference stay server-side via two NEW service-token internal
  reads on card-service** (mirroring account-service's account lookup): `GET
  /api/v1/internal/cards/{cardId}?accountHolderId=` -> {cardId, accountId, pan}
  and `GET /api/v1/internal/cards/authorisations/{authorisationId}?accountHolderId=`
  -> {authorisationId, schemeReference, status, amountMinorUnits, currency}. Both
  ownership-scoped (findBy…AndAccountHolderId), 404 -> null in the card client
  (getOrNull). The demonstrator never supplies the PAN or the scheme reference.
  Chosen over forwarding the customer access token so the sandbox keeps a single
  auth idiom (service tokens for all internal reads).
- **Refund references the settled purchase by authorisationId** (card-service's
  canonical purchase id). resolveAuthorisation returns the authorisation with its
  status; the sandbox rejects non-SETTLED with 400 CARD_PURCHASE_NOT_SETTLED(7966,
  NEW) and unknown/foreign with 404 CARD_NOT_FOUND(8993). Amount + currency are
  read from the settled authorisation (full refund), never from the request.
- **Purchase currency is Currency.GBP.name** (named constant, no magic string):
  the whole card estate is GBP in v1 and card-service does not carry the account's
  currency; resolving per-account currency is deferred until a non-GBP card account
  exists. The purchase relays the stub's APPROVED/DECLINED outcome verbatim.
- **present-now is scheme-wide + auth-gated only** (no holder header, like the
  counterparties read) — it forces ALL pending captures. The sandbox acceptance
  asserts the outgoing present-now stub call + relays the count; the actual
  "pending settles as a result" composition is proven at the STUB layer
  (StubCardSchemeAcceptanceTest.presentNowForcesAPendingPurchaseToPresentImmediately)
  and card-service (CardPresentmentConsumerTest), with the full pending->settled
  chain owned by issue-13 journey 1 (the sandbox stub is WireMock-mocked here, so
  settlement can't be proven at this layer — the issue-05 enabling-layer precedent).
- **Stub gained only the merchant read** (`GET /scenarios/cards/merchants`);
  purchase/present-now/refund stub endpoints already existed from issues 05/10.
  Four thin stub-client ops mirror them (WireMock request-construction +
  deserialisation tested). Sandbox writes are NOT idempotency-protected (matching
  direct-debits). Contract-first: 4 additive paths, oasdiff 0 err / 0 warn / 4 info.

Files: OpenAPI (+4 paths, +6 schemas); MalaiCode.CARD_PURCHASE_NOT_SETTLED(7966);
card-service (InternalCard/AuthorisationResponse models, mappers, repo finder
findByAuthorisationIdAndAccountHolderId, CardsService.resolveForHolder,
AccountAuthorisationsService.resolveForHolder, two GET handlers; happy+sad
acceptance tests; CONTEXT.md internal-resolve note); malai-card-client
(resolveCard/resolveAuthorisation + models + getOrNull; WithCards resolve stubs;
4 client tests); malai-stub (merchant GET + DTOs; acceptance test); malai-stub-client
(4 card ops + CardScenarioModels; WithMalaiStub stubs + 3 builders; 5 client tests);
sandbox-service (4 controllers, pom + config + properties + WithCards + cardBaseUrl;
15 acceptance tests).

Verification (serial mvn, Postgres + Localstack + WireMock up): full reactor
install -DskipTests EXIT 0. Green — card-service 78/0/0 (+6), sandbox-service
31/0/0 (+15), malai-stub 65/0/0 (+1), malai-card-client +4, malai-stub-client +4,
malai-api 21/0/0 (aggregator boots the new sandbox controllers + card-service
internal reads in one JVM; MalaiApiArchitectureTest + OpenApiSpecTest green).
oasdiff breaking --fail-on ERR EXIT 0 (4 INFO endpoint-added).

Notes for next iteration:
- Issue 13 journey 1/3 (e2e) unblocked: the sandbox purchase -> PENDING -> present
  -> settled and settled -> refund flows are now fully driveable from the
  authenticated surface. card-service/card-gateway still need their own e2e
  containers.
- Issue 12 (seeders): the ambient scheduler can reuse the same stub demo endpoints
  against registered PANs; the sandbox itself targets only live-holder cards.
- Deferred: per-account purchase currency (GBP-only today); a settled-purchases
  list surface for the webapp to obtain authorisationIds to refund (the demo can
  retain the id from when the purchase was pending, or issue 13 captures it inline).
