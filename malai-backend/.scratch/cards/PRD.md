# PRD: Cards — debit + credit cards with issuer-side processing, end to end

Status: ready-for-agent

## Problem Statement

Malai claims to be a credible UK retail bank, but no account has a card. For the bank executive or payments technologist watching a demo, cards are not a feature — they are *the* product: the freeze toggle, the pending coffee purchase popping up mid-conversation, the credit card statement with a minimum payment. A retail bank with no card estate reads as a prototype, and the architecture question every card-literate evaluator asks — "where does the scheme plug in, and how do you authorise in real time?" — currently has no answer anywhere in the platform.

The gap is two-sided. The customer side: no card to show, freeze, lose, or replace; no pending/declined activity in the feed; no credit product at all. The architecture side: the platform demonstrates push rails (FPS, SWIFT, BACS-DC) through per-rail Gateways, but has nothing that demonstrates issuer processing — the synchronous authorise-while-the-customer-waits flow that is structurally unlike everything the payments estate does today.

## Solution

Add a full card estate: **debit cards** auto-issued with every current-style Account, and a **credit card** product acquired through an instant-approval application flow — both with simulated merchant spend driven end to end through a real scheme seam.

A new **`card-service`** bounded context owns the **Card** entity (`ACTIVE ⇄ FROZEN → CANCELLED`, with lost/stolen auto-replacement), the issuer-side **Card authorisation** lifecycle (`AUTHORISED → SETTLED / REVERSED`, plus persisted **Declines**), and the credit-product mechanics (**Credit limit**, fully derived monthly **Statement**). Card spending is *never* a `payments-service` **Payment**: card-service drives the ledger's **Hold**/**Posting** primitives directly (ADR-0005).

The scheme integration follows the per-rail Gateway pattern — a new **`card-gateway`** module with `malai-stub` impersonating a fictional-brand card scheme and its merchants — with one deliberate, documented deviation: the **authorisation leg is synchronous HTTP end to end** (stub → gateway → card-service → decision in the response), because real issuer links are synchronous and the demo purchase must appear instantly. Presentment, reversal, and refund arrive asynchronously on `event-*` queues like every other rail.

The credit card's money position is an ordinary `account-service` **Account** (`AccountType.CREDIT_CARD`), ledger-backed, guarded by a new per-account ledger **floor** at −creditLimit. Repayment is the existing book transfer — the credit account carries ordinary identifiers, so "Pay card" needs zero new payment plumbing.

The feed tells the whole story: settled purchases are plain ledger entries (the presentment posting carries the merchant name), while pending and declined card activity composes into the account feed **server-side** in account-service (ADR-0006) — so the available balance never drops unexplained, and a frozen card's decline is *visible*.

Demonstrators drive everything from the Sandbox: trigger a purchase at a chosen merchant, force presentment now, trigger a merchant refund. Ambient card spend runs against seeded cards. Fresh registrations get a debit card; flagship demo users get the full estate. The webapp ships the complete surface: Cards nav, card art with masked PAN and reveal, freeze/replace, the credit application, statement + Pay card, and PENDING/DECLINED feed rows.

## User Stories

### Debit card — customer

1. As a malai customer, I want a debit card issued automatically when my current account is created, so that my account is spendable from day one without an ordering step.
2. As a malai customer, I want a Cards section showing each of my cards with card art, the fictional scheme mark, my name, the masked PAN (last four), and the card's status, so that my bank looks and feels like a real one.
3. As a malai customer, I want to reveal a card's full PAN, expiry, and CVV on demand, so that I can "use" the card the way I would a real virtual card.
4. As a malai customer, I want to freeze a card instantly, so that I can protect my money the moment something feels wrong.
5. As a malai customer, I want a purchase attempted on my frozen card to be declined and to *see* that decline in my account feed with the reason, so that I trust the freeze actually did something.
6. As a malai customer, I want to unfreeze my card and see the next purchase approved, so that the freeze is reversible without ceremony.
7. As a malai customer, I want to report a card lost or stolen and immediately receive a replacement card with a new PAN on the same account, so that losing a card is a ten-second problem.
8. As a malai customer, I want a card purchase to appear in my account feed within seconds of the authorisation, flagged as pending and carrying the merchant's name, so that my feed reflects reality as it happens.
9. As a malai customer, I want my available balance to drop the instant a purchase is authorised, while my ledger balance moves only at settlement, so that I always know what I can actually spend.
10. As a malai customer, I want a pending purchase to become a settled transaction when the scheme presents it, so that the pending → settled transition I know from my real bank happens here too.
11. As a malai customer, I want a pay-at-pump-style purchase that authorises more than it captures to settle for the actual amount and release the rest of the hold immediately, so that my available balance is never wrongly depressed.
12. As a malai customer, I want a merchant refund to appear as a credit in my feed carrying the same merchant name as the original purchase, so that refunds are recognisable at a glance.
13. As a malai customer, I want my card purchases categorised in my spending insights by merchant, so that card spend feeds the same money-understanding tools as everything else.

### Credit card — customer

14. As a malai customer, I want to apply for a credit card in the app with a short form and get an instant decision, so that acquiring a credit product takes seconds, not days.
15. As a malai customer, I want my approved application to atomically create a credit card account and its card with a stated credit limit, so that I can spend on it immediately.
16. As a malai customer, I want my credit card account to appear in my account list showing what I owe, my available credit, and my limit, so that my whole financial position lives in one place.
17. As a malai customer, I want to spend on my credit card up to my available credit, so that the product behaves like a real revolving credit line.
18. As a malai customer, I want an authorisation that would push me past my credit limit to be declined, so that the limit is real and not decorative.
19. As a malai customer, I want a monthly statement for my credit card showing the cycle's transactions, the statement balance, the minimum payment, and the due date, so that the product speaks the language every credit card holder knows.
20. As a malai customer, I want to pay my credit card from my current account with minimum / statement balance / custom presets, so that repaying takes one screen.
21. As a malai customer, I want my repayment to reduce what I owe immediately, so that the feedback loop between paying and owing is visible in the demo.

### Demonstrator — sandbox and seeding

22. As a demonstrator, I want to trigger a card purchase from the Sandbox by choosing a card, a merchant from the fictional directory, and an amount, so that I can narrate a deterministic purchase moment on stage.
23. As a demonstrator, I want purchases to stay visibly pending and then settle automatically after a configurable delay, so that the pending → settled story tells itself.
24. As a demonstrator, I want a "present now" control that forces pending captures to settle immediately, so that I control the pacing when the script needs it.
25. As a demonstrator, I want to trigger a merchant refund against a settled purchase, so that I can show the refund story on demand.
26. As a demonstrator, I want declines to need no magic values — freezing the card or exceeding the limit is enough — so that the decline demo is honest and repeatable.
27. As a demonstrator, I want freshly registered users to get a debit card whose seeded history already reads as card spend, so that a live sign-up lands in a believable account.
28. As a demonstrator, I want freshly registered users to *not* have a credit card, so that the application flow remains a live demo moment.
29. As a demonstrator, I want flagship seeded users to have the full estate — debit + credit card, months of credit history, a part-paid current statement, and one or two pending authorisations — so that every screen has something on it before I touch anything.
30. As a demonstrator, I want ambient card spend to run against seeded cards, so that demo accounts feel alive between my clicks.

### Bank evaluator — architecture

31. As a bank evaluator, I want issuer processing isolated in its own bounded context with its own authorisation lifecycle, distinct from payment origination, so that the service boundaries match how a real bank organises card processing.
32. As a bank evaluator, I want the card scheme reached through a dedicated gateway with the stub on the far side, so that "where would Mastercard/Visa plug in?" has a pointable answer.
33. As a bank evaluator, I want the authorisation leg to be synchronous — decision returned while the request waits — so that the integration is shaped like a real issuer link, not a queue-shaped approximation.
34. As a bank evaluator, I want card holds, partial capture, and the credit floor enforced inside the ledger, so that the book of record owns money invariants rather than trusting callers.
35. As a bank evaluator, I want PANs Luhn-valid from a dedicated BIN, masked everywhere by default, and redacted from logs and the audit chain, so that data handling looks like a bank's even though the data is fictional.
36. As a bank evaluator, I want the card API published in the hand-authored OpenAPI contract before implementation, so that the contract-first discipline holds across the platform.
37. As a bank evaluator, I want end-to-end test journeys with sequence diagrams covering authorise → pending → present → settle, apply → spend → statement → repay, and refund, so that I can trace the architecture through real wire traffic.

## Implementation Decisions

All decisions below were resolved in a `/grill-with-docs` session; the domain language is recorded in `card-service`'s CONTEXT.md, the two hard-to-reverse decisions in ADR-0005 (cards bounded context, sync auth leg) and ADR-0006 (server-side feed composition). Ledger semantics changes are reflected in `ledger-service`'s CONTEXT.md.

### Bounded context and modules

- New `card-service` under the services tree, bundled into `malai-api`. Owns: Card entity + lifecycle, Credit card application, Card authorisation lifecycle (including persisted Declines), presentment/reversal/refund consumers, derived credit statement figures, and the pending/declined read surface.
- New `card-gateway` under the services tree, bundled into `malai-api`, following the per-rail Gateway pattern: dumb, stateless, no database, correlation ids on the wire. Deviation (ADR-0005): the authorisation leg is synchronous HTTP — scheme-shaped auth request in, internal card-service call, scheme-shaped decision out. Presentment/reversal/refund webhooks produce `event-*` queues.
- New client pair `malai-card-client` / `malai-card-client-test-support`, consumed by account-service (feed composition), sandbox-service (triggers), and card-gateway (authorisation decision call).
- `malai-stub` gains the card scheme simulator: fictional merchant directory, auth origination, presentment scheduler (configurable delay + force-now), refund origination, a PAN registry (seeded cards only), and ambient card spend. All simulation *decisions* live here; the gateway decides nothing.
- Card wire contracts (auth request/response, presentment, reversal, refund, decline reason codes) live in the shared scheme-contracts module so stub and gateway cannot drift.
- New queues follow the existing catalog + app-side provisioner mechanism: `cards-scheme-event-presented`, `cards-scheme-event-reversed`, `cards-scheme-event-refunded` (each FIFO with paired DLQ). No `command-*` queues in v1 — the bank issues no instructions to the card scheme; the sync auth leg and the seeder's PAN registration cover the outbound directions.

### Deep modules (isolation-testable, stable interfaces)

- **AuthorisationDecisioner** (card-service): (card state, requested amount, ledger hold outcome) → approve, or decline with reason. All decline reasons produced here; the ledger's funds-guard rejection maps to insufficient-funds/over-limit.
- **StatementCalculator** (card-service): pure function of (cycle entries, cycle month, today) → statement balance, minimum payment (greater of £5 or 2.5% of statement balance), due date (21st of the following month). Nothing persisted at cycle close in v1.
- **PanGenerator** (card-service): Luhn-valid 16-digit PANs from a dedicated malai BIN in an unassigned range (9-series), plus expiry/CVV generation and the canonical masked form.
- **Scheme message translator** (card-gateway): pure mapping between scheme-shaped wire messages and internal card-service shapes, no I/O.

### Ledger changes

- Hold settle takes an optional settle amount ≤ the held amount; the hold is marked settled, a posting for the actual amount is recorded, and the remainder is released — one atomic ledger transaction, idempotent on the hold id. Over-capture is not supported.
- The funds guard gains a per-account floor: a posting or hold that would take available balance below the floor is rejected atomically. The credit card account registers with floor = −creditLimit. Existing policies are unchanged (`NO_OVERDRAFT` ≡ floor 0, `ALLOW_NEGATIVE` ≡ unbounded).

### Accounts, credit product, repayment

- `AccountType` gains `CREDIT_CARD`. The credit account is created only by the Credit card application (never at registration), atomically with its Card, at a fixed demo limit of £2,500. The application is theatre — instant approval, no underwriting.
- The credit account carries ordinary sort-code/account-number identifiers so repayment rides the existing book transfer unchanged; it is excluded from Confirmation of Payee eligibility.
- Statement cycle is the calendar month, aligned with the existing per-month statement endpoint; for `CREDIT_CARD` accounts the statement surface additionally carries the three derived figures, and a credit summary (owed, available credit, limit, current-cycle figures) is exposed for the account header and Pay card presets.
- Debit cards auto-issue for `PRIMARY`/`ADDITIONAL` accounts only — never savings/loan types. One active card per account in v1. Cards are born `ACTIVE` (virtual-first; no ordered/activation state). Lost/stolen cancels terminally and auto-issues a replacement with a new PAN.

### Read model and feed

- Settled purchases are ordinary ledger entries: the presentment posting writes the merchant name onto the entry, so statements, the feed, and insights categorisation work with no card-service involvement.
- Pending (`AUTHORISED`) and `DECLINED` rows are card-service data, merged into the account feed server-side by account-service via the card client (ADR-0006), flagged with status, merchant name, and card reference. Declines have zero balance effect and are visually distinct.
- Authorisations do not expire in v1; the stub always eventually presents or reverses.

### API surface (contract-first)

- The hand-authored OpenAPI contract is edited before controllers, per repo rule; CI diff-guards compatibility.
- Customer surface: list cards; card detail with reveal; freeze; unfreeze; report-lost-or-stolen (returns the replacement); create credit card application; credit summary per account. Feed and statement changes ride the existing account endpoints.
- Sandbox surface: trigger card purchase (card, merchant, amount); force presentment; trigger refund — following the existing sandbox controller + stub-client pattern.
- Internal surface: the gateway's authorisation decision call into card-service, and account-service's pending/declined lookup, both via the card client with service-JWT auth like existing internal routes.

### Security, data handling, conventions

- The PAN joins the audit redactor's patterns; PANs never appear in logs or the audit chain and are masked to last-four everywhere except the explicit reveal response. CVV/expiry are fictional data; no PCI vault theatre in v1, no step-up auth on reveal.
- Fictional scheme brand: backend naming stays generic (card scheme, card-gateway, `cards-scheme-*`); the brand name/mark is a webapp-repo decision.
- MCC travels on the scheme wire messages and is stored on the authorisation for realism, but drives nothing in v1 (categorisation stays merchant-name-based).
- Known build gotchas to respect: the malai-api architecture test's allowed callback-path pattern must admit the card gateway's webhook paths; gateway boot classes must not carry auto-configuration exclusions (they leak into malai-api — exclude in the test boot helper instead); new client components need base-url properties in every scanned configuration; new modules register in both the root build's module list and dependency management; the cross-bank sort-code lint needs the new gateway's test tree excluded.

### Seeding and ambient activity

- Registration seeder: every fresh user's auto-issued debit card is backdated to match the account's seeded history (which already reads as card spend via the merchant seed). No credit card.
- Flagship seeder: full estate — debit + credit cards, months of credit history, a part-paid current statement, one or two live pending authorisations, and PAN registration with the stub so ambient spend targets them.
- Ambient card spend (stub-side scheduler, like the BACS payroll pattern) runs only against stub-registered (seeded) PANs; live-registered users drive spend via sandbox triggers.

### E2E topology

- card-service and card-gateway each get their own boot app, image, and compose entry following the established per-service container pattern, with the standard proxy-stub, source/target lookup, and party registrations so all card traffic lands on the Kensa diagrams.

### Webapp (malai-webapp repo)

- New top-level Cards nav: card list (card art, fictional brand, masked PAN, status) and card detail (reveal, freeze/unfreeze, report lost/stolen with the replacement appearing).
- Credit account detail: owed / available credit / limit header, statement summary, Pay card flow with minimum / statement balance / custom presets over the existing transfer machinery.
- Credit card application flow ending in the new account + card on screen.
- Feed renders PENDING and DECLINED rows distinctly, with merchant names and decline reasons.
- Sandbox panel gains the three card triggers.
- TypeScript client regenerated from the updated contract; webapp-side slicing tracked in that repo.

## Testing Decisions

A good test asserts **external behaviour through the module's public surface** — the HTTP response, the message on the queue, the row in the database as a full entity — never internal wiring. Every outgoing and incoming message this feature introduces is asserted somewhere: outgoing HTTP via WireMock request verification, outgoing queue messages via queue-side assertions, incoming webhooks and queue messages by driving them in and asserting the visible outcome.

- **Acceptance (Kensa BDD, the repo's primary layer)** — every new/changed service surface: card-service (card lifecycle, application, sync authorisation decision including every decline reason, presentment/reversal/refund consumption, statement figures), card-gateway (scheme webhook in → internal call out, asserted with WireMock; webhook in → `event-*` message out, asserted on the queue), account-service (feed composition with the card client stubbed via its test-support), ledger-service (floor guard, partial-capture settle — including the atomic remainder release), sandbox-service (three triggers → stub calls asserted). Response payloads use the strict-match property pattern with no-extra-props at every level; database state asserted as full entities. Prior art: the existing payments-service, fps-gateway, and sandbox-service acceptance suites.
- **Unit tests for the four deep modules** — AuthorisationDecisioner (decision table over card states × funds outcomes), StatementCalculator (cycle-boundary and rounding edges, minimum-payment floor), PanGenerator (Luhn validity, BIN prefix, mask shape, uniqueness), the gateway's message translator (bidirectional mapping including decline-reason crosswalk). Prior art: the categoriser and identifier-calculator unit tests.
- **Client-module tests** — malai-card-client: request construction (method, URL, headers, body) and response deserialisation against WireMock, per the established client test pattern.
- **Stub simulator tests** — auth origination, presentment scheduling (delay + force-now), refund origination, PAN registry, ambient generation, mirroring the existing stub scheme tests (including the test-id propagation discipline on scheduled callbacks).
- **Repository tests** — any new repository with custom functions, per repo rule.
- **E2E (cross-stack, Kensa-diagrammed)** — three journeys: (1) debit lifecycle: sandbox purchase → sync auth through stub→gateway→card-service → pending in feed → present now → settled entry + hold released → freeze → second purchase visibly declined; (2) credit journey: apply → account + card created → purchase → statement figures → Pay card book transfer → owed drops; (3) refund: settled purchase refunded → credit entry with the same merchant.

## Out of Scope

- **Interest, fees, arrears** — no interest accrual, late fees, delinquency, or persisted statement snapshots; cycle close is not an event in v1. The derived-statement model is deliberately v2-replaceable when interest arrives.
- **Authorisation expiry** — unpresented auths never lapse (consistent with no-expiry ledger holds); the stub always presents or reverses.
- **Over-capture** — presentment never exceeds the authorised amount.
- **Card controls beyond freeze** — no merchant/category blocks, spending limits, gambling blocks, or ATM toggles.
- **Credit limit changes, real underwriting, eligibility decisioning** — the application always approves at the fixed demo limit.
- **Chargebacks and disputes** — a refund is the only merchant-initiated credit.
- **3-D Secure, tokenisation, Apple/Google Pay, PIN management, physical card logistics** — the card is virtual-first; no activation step.
- **Multiple active cards per account**, joint-card arrangements, supplementary cardholders.
- **MCC-driven categorisation** — MCC is carried and stored but unused.
- **Step-up authentication on PAN reveal** — fictional data; a reveal interaction suffices.
- **Real scheme branding** — no Mastercard/Visa marks or BIN ranges.

## Further Notes

- **Slicing order for tracer bullets**: ledger primitives (floor + partial settle) → card-service core (entity, debit issuance, sync auth decision) → wire contracts + card-gateway + stub scheme (first e2e-visible purchase) → presentment/settlement → feed composition → freeze/decline + replace → credit application + statement + repay → sandbox triggers + refund → seeders + ambient → e2e journeys → webapp estate. Each slice should leave the demo green.
- The freeze → visible decline → unfreeze loop and the ten-second credit card application are the two headline demo moments; slices that unlock them earn their place first.
- The webapp repo owns the fictional scheme brand name/mark and all UI copy; backend vocabulary stays generic per the card-service glossary (Card, Card authorisation, Presentment, Decline, Refund, Statement, Repayment).
- Domain language and invariants: `card-service` CONTEXT.md. Decisions: ADR-0005 (bounded context + sync auth leg), ADR-0006 (feed composition). Ledger semantics: `ledger-service` CONTEXT.md (hold settle-with-amount, per-account floor).
