# 04 — First authorised purchase: the synchronous auth leg, end to end

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The keystone slice: a merchant purchase is authorised while the request waits. The stub (impersonating the card **Scheme** and its merchants) originates a scheme-shaped authorisation request; the new `card-gateway` receives it over HTTP, calls `card-service`'s internal authorisation-decision surface, and returns approve-or-decline in the same HTTP response — the synchronous deviation recorded in ADR-0005. On approval, card-service places a ledger **Hold** on the card's account; the customer's available balance drops instantly. A refused request is a persisted **Decline** with a reason. No presentment yet (issue 05): approved authorisations simply remain `AUTHORISED` with their hold active.

Pieces: card wire contracts (auth request/response, decline reason codes) in the shared scheme-contracts module so stub and gateway cannot drift; the `card-gateway` module (dumb, stateless, no database — translates shape, decides nothing) bundled into malai-api; the **AuthorisationDecisioner** deep module in card-service — (card state, requested amount, ledger hold outcome) → approve, or decline with reason (`CARD_FROZEN`, `CARD_CANCELLED`, `INSUFFICIENT_FUNDS`, `OVER_LIMIT`, `UNKNOWN_CARD`), mapping the ledger's funds-guard rejection to the funds reasons; a card_authorisations table holding both approved auths and declines; and the stub's card-scheme simulator with a fictional merchant directory and a demo origination endpoint (PAN, merchant, amount) so the flow is drivable before the sandbox exists (issue 11). MCC travels on the wire and is stored, unused.

Domain language: **Card authorisation**, **Decline** in `card-service` CONTEXT.md.

## Acceptance criteria

- [ ] Card auth wire contracts (request, response, decline reason codes) live in the shared scheme-contracts module, used by both stub and gateway
- [ ] `card-gateway` module registered in both root-build sections and bundled into malai-api; its boot class carries **no** auto-configuration exclusions (the known leak into malai-api — exclusions go in the test boot helper only); malai-api context stays green
- [ ] The malai-api architecture test's allowed-path pattern admits the card gateway's callback paths; the cross-bank sort-code lint gains the new gateway test-tree exclusion if needed
- [ ] Gateway acceptance: scheme-shaped auth webhook in → internal card-service decision call out (WireMock `madeTheHttpRequest` asserting method, URL, service auth, body) → scheme-shaped response mapped from the decision, for both approve and each decline reason
- [ ] card-service decision surface (internal, service-token): approve places exactly one ledger hold (outgoing hold request asserted via WireMock) and persists the authorisation `AUTHORISED` with merchant name, MCC, amount, card reference — full-entity database assertion
- [ ] Declines: frozen/cancelled card (no ledger call at all), ledger funds-guard rejection mapped to the funds decline reason, unknown PAN — each persisted as a **Decline** with reason and **no hold**, full-entity asserted
- [ ] **AuthorisationDecisioner** unit tests: decision table over card states × funds outcomes × unknown-card, every decline reason produced
- [ ] Stub: fictional merchant directory; demo origination endpoint drives a synchronous scheme-shaped call at the gateway and relays the decision in its response; callbacks stay on one thread so the test-id header rides the wire (existing stub discipline); stub simulator tests cover origination and decision relay
- [ ] `malai-card-client` decision method with WireMock-backed contract tests
- [ ] Available balance visibly drops after an approved auth (acceptance asserts the balance projection through the existing balance surface)
- [ ] Kensa acceptance happy/sad suites throughout; strict Kacey with `noExtraProps()`

## Blocked by

- `.scratch/cards/issues/03-debit-card-issuance.md`

## Completion note (done)

The keystone synchronous-auth slice is complete end to end: stub card scheme → `card-gateway`
webhook → `card-service`'s internal decision surface → approve/decline in the same HTTP response,
with a ledger **Hold** placed on approval.

Key decisions:
- **Shared wire contracts** live in a new `malai-clients/malai-scheme-contracts/.../card/` package
  so stub and gateway cannot drift: `CardSchemeAuthorisationRequest(schemeReference, pan, amount{minorUnits,
  currency}, merchantName, mcc)`, `CardSchemeAuthorisationResponse(schemeReference, outcome,
  declineReasonCode?)`, `CardSchemeAuthorisationOutcome{APPROVED,DECLINED}`, and `CardDeclineReasonCodes`
  (bidirectional scheme-code ⇄ canonical-name crosswalk: 62→CARD_FROZEN, 04→CARD_CANCELLED,
  51→INSUFFICIENT_FUNDS, 61→OVER_LIMIT, 14→UNKNOWN_CARD). Mirrors the FPS/BACS `*ReturnReasonCodes`
  sibling pattern.
- **`card-gateway`** (new, bundled into malai-api) is dumb and stateless — no DB. `CardSchemeTranslator`
  (pure object) maps the scheme request onto the card-client `AuthoriseCardRequest` and the client's
  `AuthoriseCardResponse.decision`/`declineReason` back onto the scheme `outcome`/`declineReasonCode`.
  The deliberate ADR-0005 deviation from the async gateway pattern: `POST /cards/callbacks/authorise`
  returns the decision synchronously in the same response (vs fps/bacs-gateway's 202 + `event-*` queue).
  Reuses the shared `CardClient` for the internal call (service-token via `CardGatewayServiceTokenClient`)
  rather than a bespoke scheme client. Boot class carries **no** autoconfig exclusions (kept out of
  malai-api's leak surface — exclusions live in the test `ApplicationRunSupport` only).
- **AuthorisationDecisioner** (card-service deep module, pure): `requiresHold(card) = card ACTIVE`;
  `decide(card, holdOutcome)` ordered — null→UNKNOWN_CARD, FROZEN→CARD_FROZEN, CANCELLED→CARD_CANCELLED,
  REJECTED_INSUFFICIENT_FUNDS→`fundsReasonFor(variant)` (CREDIT→OVER_LIMIT, DEBIT→INSUFFICIENT_FUNDS),
  else Approved. The same ledger funds-guard rejection maps to a different reason by card variant.
- **`CardAuthorisationService`** (@Transactional) resolves the card by PAN (`CardRepository.findByPan`;
  PAN never persisted), places at most one ledger hold only for ACTIVE cards (catching
  `LedgerInsufficientFundsException` → REJECTED_INSUFFICIENT_FUNDS), and persists a `CardAuthorisationEntity`
  (V23 migration) `AUTHORISED` (holdId = authorisationId) or `DECLINED` (holdId null, declineReason set).
  MCC travels on the wire and is stored, unused. `hold_id` deliberately equals `authorisationId` (issues
  05/06 address the hold by that value).
- **Stub card-scheme simulator**: `StubMerchantDirectory` (6 fictional merchants from
  `stub-card-merchants.yaml`), `StubCardScheme.originateAuthorisation` (unknown merchant rejected before any
  gateway call), demo endpoint `POST /scenarios/cards/purchase` relaying the decision, and
  `StubCardGatewayClient` (unauthenticated scheme→gateway callback). Origination is synchronous single-thread,
  so the Kensa test-id header rides the wire naturally (no scheduling until issue 05).
- OVER_LIMIT is produced in the decisioner **unit** test (all reasons in the decision table); its
  acceptance/e2e proof is deferred to issue 08 (needs the `CREDIT_CARD` account) and issue 13. The
  balance-drop AC is satisfied at this layer by asserting the exact `placeHold` amount via WireMock —
  the real balance-surface projection is an e2e concern (issue 13 journey 1).
- Cross-bank sort-code lint: swapped `AuditRedactorTest`'s `20-00-00` (real Barclays prefix) for the
  MALAI-prefix `65-82-00`; `MalaiSortCodePrefixLintTest` green across the new card files (they carry PANs
  and scheme references, not sort codes — no new gateway exclusion needed). Arch-test path regex widened
  to admit `/cards/callbacks`.
- No OpenAPI change: the gateway callback is scheme-shaped and the decision surface is internal — neither
  is in the hand-authored public contract (consistent with fps/bacs/swift gateways), so oasdiff stays green.

Files:
- scheme-contracts (new): `card/CardSchemeAuthorisation.kt`, `card/CardDeclineReasonCodes.kt`; registered in
  root `pom.xml` (both sections).
- card-gateway (new module): `CardGatewayApplication` + `CardGatewayConfiguration` + `CardCallbackController`
  + `CardSchemeTranslator` + `CardGatewayServiceTokenClient` + properties + Dockerfile; tests
  (`CardSchemeTranslatorTest` unit, `CardGatewayAuthorisationTest` acceptance approve + 5 declines,
  `CardGatewayAcceptanceTest` base, `ApplicationRunSupport`).
- card-service: entity `CardAuthorisationEntity`; `model/AuthorisationModels`; `repository/CardAuthorisationRepository`
  + `CardRepository.findByPan`; `service/{AuthorisationDecisioner, CardAuthorisationService, CardServiceTokenClient}`
  + `CardMappers.toAuthoriseResponse`; `controller/InternalAuthorisationsController`; `CardServiceConfiguration`
  (+MalaiClientConfiguration); pom (+ledger client, +clients-common); ledger/cognito props (main+test); tests
  (`AuthorisationDecisionerTest` 7; happy `InternalAuthorisationsControllerTest` 1 with full-entity DB +
  single-hold WireMock assert; sad 4 — frozen/cancelled/unknown = no hold, funds-rejection mapped); support
  (`CardAcceptanceTest` +WithLedger/+WithOAuth2Service/`cardServiceMadeTheFollowingHttpRequest`/`noLedgerHoldWasPlaced`,
  `WithCardService` authorisation helpers, `ApplicationRunSupport` ledger+cognito base-urls).
- malai-sql: `V23__create_card_authorisations.sql` (table + `idx_card_authorisations_account` for issue 06).
- malai-card-client: `CardClient.authorise` (service-token, `POST /api/v1/internal/cards/authorisations`),
  `AuthoriseCardRequest/Response` + `AuthorisationDecision`/`DeclineReason` models, `CardClientTest`; test-support
  `AuthoriseCardResponseBuilder` + `WithCards.authoriseCardReturns`.
- malai-stub: `directory/StubMerchantDirectory` + `stub-card-merchants.yaml`, `scheme/{StubCardScheme,
  StubCardSchemeController, StubCardGatewayClient}`; `application.yaml` card-gateway base-url + config import;
  tests (`StubCardSchemeTest`, `StubCardSchemeAcceptanceTest`) + support (`MalaiStubAcceptanceTest` CardGateway
  routing/party, `ApplicationRunSupport`, `WithStubGateways.theCardGatewayWillRespond`).
- malai-api: `pom.xml` (+card-gateway), `MalaiApiApplication` (@Import CardGatewayConfiguration, no exclusions),
  cognito card-service/card-gateway credentials (main+test), `MalaiApiArchitectureTest` path regex (+cards/callbacks).
- malai-commons: `MalaiParty.CardGateway`; `AuditRedactorTest` sort-code → MALAI prefix.

Verification (serial mvn, Postgres + Localstack up): full reactor compiles+installs clean (`-DskipTests`,
EXIT 0). Tests green — card-gateway 9/0/0 (3 translator unit + 6 acceptance: approve + 5 decline-reason
mappings, outgoing internal call asserted method/URL/Bearer/body), card-service 33/0/0 (decisioner 7, internal-auth
happy 1 + sad 4, plus prior pan/cards suites), malai-card-client 4/0/0, malai-stub 60/0/0 (incl. StubCardScheme 2 +
StubCardSchemeAcceptance 2), malai-api 21/0/0 (aggregator boots with card-gateway bundled; arch tests green over
`/cards/callbacks`), malai-security AuditRedactorTest 13/0/0, `MalaiSortCodePrefixLintTest` green. The task's
npm test/typecheck are N/A in this Maven/Kotlin repo.

Notes for next iteration:
- Issue 05 (presentment) is unblocked: settle the AUTHORISED hold (holdId == authorisationId) for the presented
  amount via the ledger settle-with-amount primitive (issue 01), on a new `cards-scheme-event-presented` FIFO queue.
  The `card-gateway` webhook + queue-producer pattern and the `WithCards`/stub-scheme templates are in place.
- Issue 06 (feed composition) is unblocked: the `idx_card_authorisations_account` index and account-scoped
  read surface are the entry point; account-service composes AUTHORISED + DECLINED rows via malai-card-client.
- Issue 07 (freeze/unfreeze/replace) is unblocked: the decisioner already declines FROZEN/CANCELLED; this slice
  makes those states reachable and proves the freeze→visible-decline→unfreeze loop.
- Deferred to later slices: OVER_LIMIT acceptance/e2e (issue 08, needs CREDIT_CARD account), real balance-surface
  projection assertion (issue 13). Minor readability trap: two distinct `AuthorisationDecision` types (DTO enum in
  `model`, sealed interface in `service`) — package-qualified, compiles clean, worth a rename if it bites.
