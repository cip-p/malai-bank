# 03 — Create a standing order, end to end

Status: done

## Parent

`.scratch/standing-order-direct-debit-lifecycle/PRD.md`

## What to build

A customer sets up a new standing order from the webapp. Creation requires a saved payee — the payee's details are snapshotted onto the order at creation, and execution will pay the snapshot. Request: source account, payee, amount in minor units, GBP-guarded currency, reference, frequency, and a first payment date that must be today or later (it becomes the start-date anchor). The endpoint is registered with the shared Idempotency-Key interceptor, like the other production customer writes. The webapp gains a "New standing order" wizard following the existing add-payee wizard pattern — payee picker (with a path into the existing add-payee flow), source account, details, review — and the standing-orders empty state gains its create CTA.

## Acceptance criteria

- [x] OpenAPI edited before controllers; creation path and request/response schemas defined; backwards-compatibility check stays green
- [x] Creation returns the new order with status ACTIVE, snapshot fields copied from the payee, next payment date equal to the first payment date, start-date anchor set — strict response contract and full-entity database assertion; audit event recorded
- [x] First payment date in the past is rejected; unknown or not-owned payee and account are rejected without leaking existence; non-GBP currency rejected
- [x] Endpoint registered with the shared Idempotency-Key interceptor; replay, in-flight and payload-mismatch behaviour covered at this route
- [x] Kensa acceptance tests in the service's existing happy/sad style
- [x] Webapp: wizard at a dedicated route following the add-payee wizard pattern (payee picker → source account, defaulting sensibly → amount, reference, frequency, first payment date → review and confirm); success invalidates the standing-orders query and returns to the list; empty state gains the CTA
- [x] Vitest coverage for the new hooks and wizard components per the existing two-layer convention; Playwright spec for the creation journey; accessibility spec covers every wizard step in light and dark themes

## Progress

2026-07-07 — backend slice landed on `main` (POST /api/v1/standing-orders). Request
field is `startDate`, not `firstPaymentDate` — the service CONTEXT.md glossary marks
"first payment date" as a term to avoid; the OpenAPI schema follows the glossary.
New MalaiCodes: 7972 STANDING_ORDER_START_DATE_IN_PAST, 7971
STANDING_ORDER_CURRENCY_NOT_SUPPORTED. No migration needed (V11+V17 already carry
every column). oasdiff clean. Remaining: the webapp wizard half (malai-webapp repo).

2026-07-08 — webapp wizard half landed. `POST /api/v1/standing-orders` added to the
checked-in `openapi.yaml` snapshot (endpoint + `StandingOrderCreateRequest`; only the
issue-03 slice — the backend source has since also grown issue-04's payee 409, the
sandbox direct-debit lodge and the Category enum, which are left for their own
iterations) and the client regenerated (`postApiV1StandingOrders*`, additive only).

Key decisions:

- New `useCreateStandingOrder` mirrors `useCreatePayee`: invalidates the standing-orders
  query, toasts "Standing order created.", mints an `Idempotency-Key`, sends
  `MAL-Account-Holder-Id`; `navigateOnSuccess` defaults true but the wizard opts out and
  shows a success screen (add-payee pattern).
- `NewStandingOrderPage` at `/move/standing-orders/new`: four StepFlow steps — payee
  picker (radio list + a link into `/move/payees/new`, empty state when no payees) →
  source account (pre-selects the first account) → amount (`AmountInput`/`parseAmount`) +
  frequency (radiogroup, default MONTHLY) + first-payment date (native `type=date`,
  `min`=today, today-or-later guard) + optional reference (≤80) → review + confirm.
  Request always carries `currency: "GBP"`; `reference` omitted when blank.
- `StandingOrdersPage` gained a header CTA and an empty-state CTA (both → the wizard);
  the old "empty state has no create CTA" test was rewritten to assert the mandated CTA.

Verification: `npm run test` 799 green (5 hook + 11 wizard + updated page), `npm run
typecheck` and `eslint` clean. Playwright creation journey (add payee → set up SO → see
it listed) + the wizard-step a11y sweep (light+dark) are written following the existing
specs but NOT executed here — this environment has no local malai-api/Postgres/Cognito
stack, so they need a live run to confirm the create round-trip and axe cleanliness,
the way issues 01/02 were verified live before merge.

## Blocked by

- 01 — cancel a standing order (schema migration and response-schema additions land there)
