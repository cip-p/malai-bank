# PRD: Standing-order and direct-debit lifecycle

Status: ready-for-agent

Grilled to shared understanding on 2026-07-06. Domain vocabulary lives in the standing-order-service and direct-debit-service `CONTEXT.md` glossaries; the load-bearing architectural decision is recorded in ADR 0004 (executions run through the real payments machinery). This PRD spans two repos: backend behaviour here, UI in `malai-webapp` (note: the workspace docs say `malai-webapp-v1`, but the directory on disk is `malai-webapp`).

## Problem Statement

Malai's demo accounts have standing orders and direct debits that are set dressing: they can be listed, but nothing ever fires. A bank executive watching a demo — or registering and exploring on their own — sees a feed whose only activity is backdated seed history, standing-order and direct-debit pages that can never change, and no way to demonstrate the recurring-payment lifecycle a real bank lives on. The illusion of a living bank breaks exactly where retail banking is most habitual: the rent going out, the energy bill being collected.

## Solution

Give both products a real lifecycle, split by originator the way UK banking actually works. Customers create and cancel standing orders in the webapp; direct debits are creditor-originated, so customers can only view and cancel them, while mandates are lodged through the Sandbox surface by a demo driver (and by the seeders). Per-minute bank-side schedulers execute due occurrences through the genuine payments machinery — standing-order occurrences as outbound FPS/book-transfer payments, direct-debit collections as inbound Bacs direct-debit messages originated by the stub — so every execution lands in the transaction feed the same way any other payment does, produces payment records and audit events, and survives a technologist lifting the lid. Seeded and freshly-registered accounts get schedules that continue their seeded history's rhythm, so demo accounts feel alive from the first minute and stay alive across days.

## User Stories

### Standing orders — customer

1. As a Malai customer, I want to create a standing order to one of my saved payees, so that money I send regularly goes out without manual effort.
2. As a Malai customer, I want to choose which of my accounts funds a standing order, so that rent comes from my current account and not my savings.
3. As a Malai customer, I want to set the amount, reference, frequency (weekly, monthly, quarterly, annual) and first payment date when creating a standing order, so that the schedule matches my real obligation.
4. As a Malai customer, I want a standing order whose first payment date is today to execute within about a minute, so that I can see it working immediately.
5. As a Malai customer, I want a monthly standing order anchored on the 31st to pay on the last day of shorter months and return to the 31st afterwards, so that month-end schedules behave the way my real bank's do.
6. As a Malai customer, I want to add a new payee from within the standing-order creation flow, so that setting up a payment to someone new is one journey, not two.
7. As a Malai customer, I want each executed occurrence to appear in my account's transaction feed with a running balance, so that scheduled payments are indistinguishable from ones I sent by hand.
8. As a Malai customer, I want to cancel a standing order behind a confirmation step, so that a stray tap doesn't stop my rent.
9. As a Malai customer, I want a cancelled standing order to leave my list while its past payments remain in the feed, so that my list shows only live instructions but my history stays intact.
10. As a Malai customer, I want cancelling an already-cancelled standing order to be a harmless no-op, so that retries and double-taps never error.
11. As a Malai customer, I want a standing order that fails for lack of funds to skip that occurrence and stay active, so that one tight month doesn't silently kill the instruction.
12. As a Malai customer, I want to be blocked from deleting a payee that an active standing order pays, with a clear explanation, so that I can't strand a live instruction.
13. As a Malai customer, I want a double-submitted creation request to result in exactly one standing order, so that a flaky connection doesn't double my rent.
14. As a Malai customer, I want the scheduled-payments peek on my account detail page to exclude cancelled items, so that it reflects what will actually happen.

### Direct debits — customer

15. As a Malai customer, I want to see each direct debit's creditor, amount, frequency and next collection date, so that I know what will leave my account and when.
16. As a Malai customer, I want to cancel a direct debit behind a confirmation that reminds me to also tell the company, so that I understand cancelling the mandate doesn't cancel my contract.
17. As a Malai customer, I want a cancelled direct debit to stay in my list greyed out and marked Cancelled, so that I keep a visible record of mandates I've revoked.
18. As a Malai customer, I want each collection to appear in my transaction feed on the day it's collected, so that my bills show up the way they do at my real bank.
19. As a Malai customer, I want a collection against insufficient funds to be returned unpaid without touching my balance, so that a creditor can't overdraw me.
20. As a Malai customer, I want no "create direct debit" affordance anywhere, so that the product reflects reality: only the company I'm paying can set one up.
21. As a Malai customer, I want cancellation to stop all future collections immediately, so that a revoked mandate can never pull money again.

### Ownership and safety

22. As a Malai customer, I want standing orders and direct debits I don't own to be invisible to me — unknown and not-mine both answer "not found" — so that no tenant can probe another's data.
23. As a support agent, I want list responses to include cancelled standing orders and their statuses, so that I can answer "what happened to my payment" questions.

### Demo driver — sandbox

24. As a demo driver, I want to lodge a direct-debit mandate from the Sandbox by picking a fictional creditor from the counterparty directory, so that I can demo the creditor-originated lifecycle live.
25. As a demo driver, I want to set the mandate's account, amount, frequency and first collection date, so that I can shape the demo's rhythm.
26. As a demo driver, I want a mandate with a first collection date of today to collect within about a minute, so that the audience sees the bill land during the meeting.
27. As a demo driver, I want to drain an account and show the next collection bounce with a Bacs-style return reason, so that I can demo failure handling honestly.
28. As a demo driver, I want the canonical seeded account's standing orders and direct debits to actually fire while the environment runs, so that a long-lived demo environment accumulates believable activity.
29. As a demo driver, I want seeded next-payment and next-collection dates to continue the rhythm already visible in the seeded feed history, so that the first live occurrence doesn't visibly break the pattern.

### Evaluators

30. As a bank executive registering and exploring on my own, I want my fresh account to come with a few payees, standing orders and mandates, so that the pages aren't empty shells.
31. As a bank executive who keeps an environment for a few days, I want scheduled activity to keep landing in my feed, so that the bank feels alive beyond the first session.
32. As a bank technologist evaluating the platform, I want standing-order executions to flow through the same payment initiation, holds, queues and settlement as customer-initiated payments, so that there is no "pretend payment" seam to find.
33. As a bank technologist, I want direct-debit collections to arrive as inbound scheme messages through the gateway and queue layer, so that the integration architecture mirrors real Bacs topology.
34. As a bank technologist, I want executions to be exactly-once by construction (deterministic per-occurrence identifiers colliding with existing uniqueness guards), so that at-least-once scheduling is demonstrably safe.
35. As a bank technologist, I want scheduled jobs lock-coordinated across horizontally scaled instances, so that scaling out never double-fires.
36. As a bank technologist, I want creations, cancellations, executions and returns in the audit chain, so that every state change is accountable.

### Platform

37. As a platform operator, I want an environment recovering from downtime to fire each overdue instrument exactly once and resynchronise its schedule to the future, so that wake-up produces a lively feed rather than a pile of triple rent payments.
38. As a platform operator, I want a re-fired occurrence (crash between initiation and schedule advance) to bounce harmlessly off the uniqueness guard, so that recovery needs no manual reconciliation.
39. As a webapp developer, I want the OpenAPI contract extended before controllers change, so that the generated TypeScript client and backwards-compatibility checks stay trustworthy.
40. As a customer using assistive technology, I want every new screen and dialog to pass accessibility checks in both light and dark themes, so that the demo's polish extends to accessibility.

## Implementation Decisions

**Originator split.** Standing orders are customer-originated: create and cancel are customer-facing API + webapp features. Direct debits are creditor-originated: the customer-facing surface is list and cancel only; mandate lodgement is a Sandbox surface action (fictional creditor from the counterparty directory, target account of the active holder, amount, frequency, first collection date) written synchronously bank-side. No AUDDIS-style mandate messaging.

**Cancellation is a state transition.** Both tables gain `status` (ACTIVE/CANCELLED) and a nullable cancellation timestamp. Nothing is ever deleted. Schedulers only scan ACTIVE rows. Lists return all rows with status; presentation policy (hide cancelled standing orders, grey out cancelled mandates) lives in the webapp.

**Executions run through the real payments machinery** (ADR 0004). A standing-order occurrence is an outbound payment initiated via a new internal payments-service endpoint, reached through a new payments client module pair — FPS with hold and async settlement for external counterparties, book transfer for on-us, reusing existing routing and currency guards. A direct-debit collection is an inbound payment on a new BACS direct-debit rail: the direct-debit scheduler asks the stub (via the stub client) to originate the collection, which travels gateway → queue → consumer into a new inbound-debit path that debits the customer, credits the scheme-settlement suspense account, and records a settled inbound payment.

**Schedulers.** One per owning service, per-minute, ShedLock-coordinated (the ArchUnit rule already enforces the lock annotation). Dueness is date-based against the injected time service, so acceptance tests drive execution by stubbing time. One-fire resync: a due row fires exactly one occurrence, then its next date jumps to the first occurrence strictly in the future — missed occurrences are never caught up.

**Schedule arithmetic.** Both tables gain an immutable `start_date` anchor; every occurrence is anchor + n × frequency with month-end clamping in shorter months. Execution happens on any calendar day — a deliberate fidelity break; no working-day calendar. First payment date on creation must be today or later.

**Failure semantics.** A standing-order occurrence that cannot place its hold is a missed occurrence: failed payment record plus audit event, no retry, schedule advances, instruction stays active. A collection exceeding available funds is returned unpaid (returned payment record with an insufficient-funds reason, return event flowing back out through the gateway); nothing posts to the customer account, the mandate stays active, the schedule advances. Returned occurrences do not appear in the transaction feed — payment state and audit only.

**Idempotency** (per the BACK-07 posture: judge per endpoint, don't reach for the interceptor by default). Standing-order creation is registered with the shared Idempotency-Key interceptor. Cancels rely on natural idempotency (repeat cancel is a success no-op). Sandbox writes stay unprotected like the existing ones. Execution-level exactly-once comes from deterministic per-occurrence end-to-end IDs — a function of instrument ID and occurrence date — colliding with the payments table's unique end-to-end key outbound and the queue-message dedup table inbound. Schedulers initiate first, advance second.

**API contract** (OpenAPI edited first; all changes additive). New: standing-order creation (account, payee, amount in minor units, GBP-guarded currency, reference, frequency, first payment date); DELETE-style cancel for both products returning 204, idempotent, 404 for unknown or not-owned; sandbox mandate lodgement. Existing list response schemas gain status, cancellation timestamp and start date. No single-resource GETs, no amend.

**Payee coupling.** Standing-order creation requires a saved payee; counterparty details are snapshotted onto the order at creation and execution pays the snapshot. Deleting a payee with an ACTIVE standing order is rejected with a conflict and a dedicated error code; the foreign key moves to set-null-on-delete so cancelled orders never block deletion.

**Mandate amounts.** Fixed-amount mandates only; the nullable amount column stays as future headroom with no behaviour behind it.

**Module additions.** New payments client module pair for the standing-order executor's internal call; new internal direct-debit client for the sandbox lodgement path; stub-client and stub extensions for collection origination; BACS gateway additions for the new rail's message translation — all registered per the module-pair convention.

**Seeders.** The canonical demo seeder populates the new columns, derives next dates from the anchor rhythm its own transaction history establishes, and links seeded standing orders to seeded payees. The registration seeder additionally creates a few payees, linked standing orders and mandates for fresh users, earliest next dates one to three days out, keeping its existing idempotency guard.

**Webapp.** Standing-order creation wizard following the existing add-payee wizard pattern (payee picker with a path into payee creation → source account → amount, reference, frequency, first payment date → review); cancel affordances with confirmation dialogs on both list pages, direct-debit copy warning the customer to also tell the company; cancelled mandates greyed with a badge; direct-debit empty state remains CTA-less; a third Sandbox tab for lodging mandates; the scheduled-payments peek excludes cancelled items. All data access through the regenerated client and the established hook conventions.

## Testing Decisions

Tests assert external behaviour at established seams — HTTP responses, full database entities, and every message crossing a boundary in either direction — never internal wiring. The seams are the existing ones; no new test seams are introduced. These seams were reviewed and agreed during design.

- **Standing-order service acceptance (Kensa BDD, the service's existing style):** creation (strict response contract with no extra properties, full-entity database assertion, audit event, idempotency-key registration, payee-not-found, ownership 404s); cancellation (transition, repeat-cancel no-op); executor under stubbed time — asserting the exact outgoing HTTP request to the payments internal endpoint including the deterministic end-to-end ID, schedule advance, failure path leaving the instruction active, skip-missed resync arithmetic, and that cancelled rows are never scanned.
- **Direct-debit service acceptance:** cancellation paths; collection requester under stubbed time — exact outgoing request to the stub asserted, schedule advance, cancelled mandates skipped.
- **Payments service acceptance (prior art: the existing inbound Bacs credit and outbound FPS tests, which publish real queue messages and assert outgoing requests and full payment entities):** internal initiation endpoint placing the hold and publishing the outbound command; duplicate end-to-end ID bouncing; inbound direct-debit collection posting debit-customer/credit-suspense and settling; insufficient-funds collection producing the outbound return event and no posting; dedup replay.
- **Gateway tests** for the new rail, following the existing per-rail gateway pattern: command-in to scheme-call-out, scheme-event-in to queue-event-out.
- **Client module tests** (prior art: existing client pairs): request construction and response deserialisation for the new payments client, the internal direct-debit client, and the stub-client extension, with test-support builders.
- **Repository tests** for every new custom query (the due-scan queries), per the repo rule.
- **Cross-stack end-to-end: one real-clock journey per product** — create a standing order with first payment today and poll the feed until the line appears; lodge a sandbox mandate collecting today and poll for the collection. Feasible because of the per-minute cadence.
- **Webapp:** Vitest for new hooks (mocking the generated SDK through a real query client) and components (mocking feature hooks), per the existing two-layer convention; Playwright specs for the creation wizard, both cancel flows and the sandbox tab; the accessibility spec extended to every new screen and dialog in both light and dark themes.

## Out of Scope

- Amending a standing order or mandate (amount, frequency, or date changes) and single-resource GET endpoints.
- Variable-amount collection behaviour — the schema headroom remains, with nothing behind it.
- Working-day execution: no weekend rolling, no bank-holiday calendar.
- Retry loops for failed occurrences, instrument suspension, and customer notifications.
- Transaction-feed visibility for returned occurrences (payment state and audit only).
- AUDDIS-style mandate-lodgement messaging and stub-owned collection schedules — the stub remains the apparent originator only.
- Customer-facing direct-debit creation in any form.
- Server-side list filtering by status.
- Catch-up execution of occurrences missed during downtime.

## Further Notes

- Backend work lands in this repo; UI work lands in `malai-webapp` (directory name on disk; workspace docs call it `malai-webapp-v1`). Sequence: OpenAPI contract first, backend implementation, client regeneration, then webapp features.
- The demo-critical beats this feature exists for: create a standing order paying today and watch the feed within a minute; lodge a mandate collecting today and watch the bill land; drain an account and show the return; leave an environment running and find accumulated scheduled activity days later.
- Glossary terms (occurrence, missed occurrence, collection, return, mandate, payee snapshot, start date) are defined in the two services' `CONTEXT.md` files; ADR 0004 records why executions go through the real rails and why a re-fired duplicate bouncing off the uniqueness guard is the mechanism, not a bug.
