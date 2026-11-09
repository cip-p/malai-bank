# 09 — Credit statement figures and Pay card repayment

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The credit card speaks the language every cardholder knows: for a **Credit card account**, the monthly **Statement** carries three derived figures — **Statement balance** (amount owed at cycle close), **Minimum payment** (the greater of £5 or 2.5% of the statement balance), and the **Due date** (the 21st of the following month). The cycle is the calendar month, aligned with the existing per-month statement surface; nothing is persisted at cycle close (no interest in v1, so cycle close is not an event). The credit summary from issue 08 additionally carries the current-cycle figures so the webapp's Pay card presets have everything on one call.

The figures come from the **StatementCalculator** deep module: a pure function of (cycle entries/closing position, cycle month, today). **Repayment** is deliberately zero new payment plumbing: a book transfer from a current-style account to the credit account's identifiers (the account was made addressable in issue 08) — this slice *proves* that path end to end and asserts the owed figure drops. Overpaying into positive balance is legal (the floor restricts only the negative direction).

Domain language: **Statement**, **Repayment** in `card-service` CONTEXT.md.

## Acceptance criteria

- [ ] OpenAPI edited first: statement response gains the three credit figures for `CREDIT_CARD` accounts (absent for other types), credit summary gains current-cycle figures; all additive, `oasdiff` green
- [ ] **StatementCalculator** unit tests: minimum payment floor (£5), the 2.5% branch and its rounding, crossover point between the two, due date is the 21st of the following month across month lengths and year end, zero owed → zero minimum, credit (positive) balance → zero minimum, month-boundary entries land in the correct cycle
- [ ] Statement surface for a `CREDIT_CARD` account returns the cycle's entries plus the three figures, consistent with the ledger closing position for that month — asserted with stubbed time for determinism
- [ ] Statement surface for non-credit accounts is byte-compatible with today (regression, no figures leak in)
- [ ] Repayment end to end: a book transfer from the holder's current account to the credit account's identifiers settles atomically; the credit account's ledger balance rises, owed drops, and the credit summary reflects it — outgoing/incoming calls asserted per the composition's existing patterns
- [ ] Repayment appears in both accounts' transaction feeds (existing book-transfer behaviour, asserted for the credit account)
- [ ] Overpayment beyond the owed amount is accepted and yields a positive credit balance with zero minimum payment
- [ ] Ownership verification unchanged on all touched surfaces; strict Kacey with `noExtraProps()`

## Blocked by

- `.scratch/cards/issues/05-presentment-settles-the-purchase.md`
- `.scratch/cards/issues/08-credit-card-application.md`

## Completion (done)

Delivered as designed. Key decisions:

- **StatementCalculator lives in `card-service`** (pure `object`, per the PRD's "four deep
  modules" + CONTEXT.md line 58 "statement mechanics belong to this context"). `figuresFor(
  closingBalanceMinorUnits, cycleMonth) -> StatementFigures(statementBalance, minimumPayment,
  dueDate)`. owed = -closing; minimum = owed<=0 ? 0 : max(500, roundHalfUp(owed*2.5%)) via
  integer `(owed*25 + 500)/1000`; dueDate = 21st of the month after the cycle.
- **account-service composes via the card client** (issue-06 precedent). It exposes a new
  service-token internal endpoint `GET /api/v1/internal/cards/statement-figures?
  closingBalanceMinorUnits=&cycleMonth=` (not in the public OpenAPI). account-service passes
  the ledger **closing position it already reads** (single read, zero drift: the statement
  body's `closingBalanceMinorUnits` and the figures' `statementBalance` are the same number
  by construction) and card-service applies the **rules**. Considered-and-rejected: folding
  the calculator into account-service (simpler, but contradicts the PRD's explicit card-service
  ownership and issue-08's card-client composition note). The endpoint is deliberately thin —
  a rule engine over the wire — because the rules (min-payment %, due-date policy) evolve in
  card-service when interest/fees arrive in v2.
- **Fail-open** like the feed authorisations (issue 06): a card-service outage drops the three
  nullable figures and still returns the statement body / ledger position. Figures are nullable
  -> omitted (`spring.jackson.default-property-inclusion=non_null`) for every non-`CREDIT_CARD`
  statement, so the non-credit statement stays byte-compatible (existing PRIMARY tests pass
  with no card stub == regression guard).
- **Repayment is zero new production code**: a `POST /transfers` to the `CREDIT_CARD` account's
  ordinary identifiers (issue 08) resolves same-holder -> `Rail.BOOK_TRANSFER` -> ledger
  `+amount` credit leg; the `FLOORED(-250000)` guard permits the balance rising toward zero.
  Proven at acceptance level (outgoing payments call asserted with the CREDIT_CARD destination)
  + the repayment credit row appears in the credit account's feed. `owed = -ledgerBalance`
  mechanically drops after a `+amount` credit; the stateful owed-drop-by-paid-amount is
  issue 13 journey 2 (e2e), as scoped.
- **Overpayment**: `owed = -ledgerBalance` goes negative (credit); calculator returns a
  negative statementBalance and zero minimum. Proven by the calculator unit test + a
  credit-summary acceptance test (ledgerBalance +5000 -> owed -5000, min 0).
- **Contract-first, additive only**: `StatementResponse` + `CreditSummaryResponse` each gain
  three optional fields (`statementBalanceMinorUnits`, `minimumPaymentMinorUnits`, `dueDate`
  string/date). oasdiff 0 ERR / 0 WARN / 6 INFO (`response-optional-property-added`). No SQL
  migration (no new persisted state; cycle close is not an event in v1).

AC note: "month-boundary entries land in the correct cycle" is covered at the acceptance level
(a `CREDIT_CARD` statement for `yyyymm` derives its figures from that month's ledger closing
position via the existing `parseStatementPeriod` `[start, end)` query) rather than as a
StatementCalculator unit test, because the calculator takes the pre-bucketed closing balance
(single source of truth) rather than raw entries.

Files:
- OpenAPI: `StatementResponse` + `CreditSummaryResponse` gain the three optional figures.
- card-service: `statement/StatementCalculator.kt` (+`StatementFigures`); `model/
  StatementFiguresResponse.kt`; `controller/InternalStatementFiguresController.kt`; tests —
  `StatementCalculatorTest` (11), happy `InternalStatementFiguresControllerTest` (2).
- malai-card-client: `CardClient.getStatementFigures` + `StatementFiguresResponse` model;
  `CardClientTest.getStatementFigures`.
- malai-card-client-test-support: `StatementFiguresResponseBuilder`;
  `WithCards.getStatementFiguresReturns` / `...ReturnsServerError`.
- account-service: `CreditSummaryResponse` + `StatementResponse` gain the three nullable
  fields; `AccountsService` gains `creditStatementFigures` (fail-open) + `currentCycleMonth`,
  wired into `getCreditSummary` (current cycle) and `getStatement` (`CREDIT_CARD` only); tests
  — happy `AccountsControllerTest` (+3: statement figures, statement fail-open, credit-account
  repayment feed row), happy `CreditCardApplicationControllerTest` (3 credit-summary tests now
  assert the figures + card call; +2: overpayment, fail-open), happy `TransfersControllerTest`
  (+1: repayment to the CREDIT_CARD account delegates as a book transfer).

Verification (serial mvn, Postgres + Localstack + WireMock up; npm test/typecheck N/A in this
Maven/Kotlin repo): card-service 64/0/0 (51 -> 64; +13), account-service 167/0/0 (161 -> 167;
+6), malai-card-client 6/0/0 (5 -> 6), malai-api 21/0/0 (aggregator boots the new controller +
composition in one JVM; MalaiApiArchitectureTest + OpenApiSpecTest green). oasdiff breaking
`--fail-on ERR` EXIT 0 (6 INFO additive).

Notes for next iteration:
- Issue 12 (seeders): the flagship "part-paid current statement (owed > 0, minimum > £5 branch)"
  is now computable — seed a CREDIT_CARD account whose settled history leaves owed > £200 so the
  2.5% branch (not the £5 floor) is exercised.
- Issue 13 journey 2 (e2e) owns the stateful proof: apply -> spend -> statement figures satisfy
  the derived formulas against seeded amounts -> Pay-card book transfer -> owed-drops-by-paid.
  The figures endpoint has no e2e wiring of its own (account-service composes it in-JVM), but
  card-service/card-gateway still need their own e2e containers per issue 13.
- The thin `statement-figures` endpoint is a candidate /code-review "could be a library" flag;
  it is deliberate (bounded-context ownership; rules grow in card-service with interest/fees).
