# 06 — Pending and declined card activity in the account feed

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The feed tells the whole card story (ADR-0006): a purchase appears in the account transactions feed the moment it is authorised — flagged `PENDING`, carrying the merchant name — and a refused purchase appears flagged `DECLINED` with its reason and zero balance effect. Without this, the available balance drops unexplained and the freeze demo's decline is invisible.

`account-service`'s transactions endpoint — already a composition over the ledger — additionally pulls the account's in-flight (`AUTHORISED`) and `DECLINED` card authorisations from a new internal per-account read surface on `card-service` (via `malai-card-client`) and merges them into the feed newest-first. Settled entries are untouched (they are ledger rows already). The feed follows its existing fail-open posture: if card-service is unavailable, the ledger entries still return.

The feed item shape gains additive fields: a status discriminator (settled rows keep today's shape), merchant name for card rows, the decline reason, and a card reference so the UI can link to the card.

## Acceptance criteria

- [ ] OpenAPI edited first; feed changes are additive, `oasdiff` stays green
- [ ] New internal per-account read on card-service (service-token) returning that account's `AUTHORISED` and `DECLINED` authorisations; any custom repository functions tested
- [ ] `malai-card-client` gains the per-account read with WireMock-backed request-construction + deserialisation tests
- [ ] account-service acceptance: feed merges ledger entries + pending + declined newest-first; `PENDING` rows carry merchant name and amount but no running balance mutation; `DECLINED` rows carry the decline reason and provably zero balance effect; the outgoing card-service request is asserted (`madeTheHttpRequest`)
- [ ] `SETTLED` authorisations are never merged from card-service (their ledger entry already represents them — no double rows)
- [ ] Fail-open: card-service unavailable → feed returns ledger entries only (consistent with the feed's existing posture), asserted
- [ ] Ownership: the internal read is scoped by account, and the public feed's existing ownership verification is unchanged
- [ ] Strict Kacey `containsProp(json { … noExtraProps() })` on the merged feed shape, including one test with all three row kinds present

## Blocked by

- `.scratch/cards/issues/04-first-authorised-purchase.md`

---

## Completion note (2026-07-12)

Done. `account-service`'s transactions feed now composes the account's in-flight (`AUTHORISED`)
and `DECLINED` card authorisations server-side (ADR-0006), flagged `PENDING`/`DECLINED`, merged
newest-first with the ledger rows. Settled purchases stay plain ledger rows (never merged). The
feed keeps its fail-open posture: card-service down → ledger rows only.

Key decisions:
- **Contract (oasdiff-verified, not guessed).** `TransactionItemResponse` is *shared* by the feed
  AND the statements endpoint, and its `entryId`/`transactionId`/`runningBalanceMinorUnits` are
  `required`. Empirically confirmed against the pinned `tufin/oasdiff:v1.10.6 --fail-on ERR` gate
  that relaxing any of those to optional is a `response-property-became-optional` **ERR** (×6, both
  endpoints) — so card rows must POPULATE the required ledger fields rather than omit them. The only
  OpenAPI change is three NEW optional fields (`status` enum `[PENDING,DECLINED]`, `declineReason`,
  `cardId`) → provably additive (6 INFO `response-optional-property-added`, 0 ERR). "Settled rows keep
  today's shape" is honoured literally: a plain ledger row carries none of the three (absence ⇒ settled).
- **Card rows fill the required ledger fields.** `entryId = transactionId = authorisationId` (a stable
  row id); `amountMinorUnits = -authorised` (debit-negative, matching the settled presentment posting);
  `runningBalanceMinorUnits = carry-forward` — the running balance of the nearest settled entry
  at-or-before the row, so a PENDING/DECLINED row shows the prevailing balance UNCHANGED (this is the
  "no running-balance mutation" / "provably zero balance effect" ACs, asserted: both card rows show
  8001, the last settled balance; the ledger sequence 8001→7501 is untouched).
- **New card-service internal read (service-token).** `GET /api/v1/internal/cards/authorisations
  ?accountId=` on `InternalAuthorisationsController` → `AccountAuthorisationsService` (read-only) →
  `findByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, [AUTHORISED, DECLINED])`, backed by the
  V23 `idx_card_authorisations_account` index. SETTLED is excluded by the query (never double-rows),
  scoped by account, newest-first. Internal endpoint (behind the `@Order(1)` ServiceJwtFilter,
  onboarding.write scope) — NOT in the public OpenAPI, like every other internal endpoint. The derived
  finder is exercised end-to-end by the card-service acceptance test (seeds AUTHORISED + DECLINED +
  SETTLED for the account plus one for another account, asserts exactly the two returned newest-first),
  matching the established card-service precedent for derived queries (issues 03/04).
- **account-service is already wired** to `malai-card-client` (+ test-support) from the issue-03 issue
  hook, so no pom/base-url changes. The card read is folded into `getTransactions` with the SAME
  fail-open idiom as the insights `categorise` call (`try { … } catch (Exception) { log "fail open";
  emptyList() }`). Ownership is unchanged — `verifyAccountOwnership` runs first; the card read is scoped
  by the already-verified accountId.
- **`declineReason`/`currency` cross the wire as strings** (enum `.name`) — matches the `category`
  string precedent (narrowable without a break) and the currency `.name` convention. Client timestamps
  are `String` (`createdAt`), matching `LedgerEntryProjection.postedAt` (the base client mapper has no
  JavaTimeModule).
- No CONTEXT.md change (it already documented feed composition); ADR-0006 already existed.

Files:
- OpenAPI: `TransactionItemResponse` +status/declineReason/cardId (optional); transactions path
  description notes the merge + fail-open.
- card-service: `AuthorisationModels` (AccountAuthorisations{,Response} DTOs); `CardAuthorisationRepository`
  finder; `CardMappers.toAccountAuthorisationResponse`; `AccountAuthorisationsService` (new);
  `InternalAuthorisationsController` GET; happy acceptance test (list newest-first, SETTLED + other-account
  excluded).
- malai-card-client: `CardClient.listAccountAuthorisations`; `CardModels` (AccountAuthorisations{,Response}
  + `AuthorisationStatus` enum); `CardClientTest` (GET url incl. `?accountId`, Bearer, deserialisation).
- malai-card-client-test-support: `WithCards.listAccountAuthorisationsReturns` + `…ReturnsServerError`;
  `AccountAuthorisation{,s}ResponseBuilder` (new).
- account-service: `TransactionsResponse` +status/declineReason/cardId + `TransactionStatus` enum;
  `AccountsService.getTransactions` merge + `cardAuthorisationItems` (fail-open) + `mergeNewestFirst`
  (carry-forward) + `toCardFeedItem`; happy tests (merge all-three-kinds + carry-forward + madeTheHttpRequest;
  fail-open); 3 existing feed tests stub an empty card read.

Verification (serial mvn, Postgres + Localstack + WireMock up; npm test/typecheck N/A in this Maven/Kotlin
repo): full reactor compiles + installs clean (-DskipTests, EXIT 0). Tests green — card-service 40/0/0,
malai-card-client 5/0/0, account-service 147/0/0, malai-api 21/0/0 (aggregator boots with the new read
bean + GET endpoint in one JVM; arch tests green), card-gateway 11/0/0 (client consumer regression).
oasdiff `--fail-on ERR` EXIT 0 (6 INFO additive).

Notes for next iteration:
- Issue 07 (freeze/unfreeze/replace) unblocked: makes FROZEN/CANCELLED reachable; a frozen-card decline
  now shows as a `DECLINED` feed row via this composition — the freeze→visible-decline→unfreeze loop is
  demonstrable end-to-end once 07 lands.
- Issue 09 (statements) shares `TransactionItemResponse`: the three new fields are already advertised on
  the statement item schema as optional-and-never-emitted (statement rows are pure ledger), so statements
  stayed byte-compatible — keep it that way (don't leak card fields into statements).
- Carry-forward edge: a card row OLDER than every returned ledger entry falls back to the oldest ledger
  balance (benign; pending auths are recent by nature). If the 100-row cap ever truncates below a live
  pending, revisit.
