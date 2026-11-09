# Payments

The bounded context that owns money movement *between* accounts — whether both endpoints are at malai (book transfer), one endpoint is at another UK bank (FPS), or one is overseas (SWIFT / SEPA, stubbed in v1). `account-service` owns the *accounts and the ledger that the money sits in*; `payments-service` owns the *journey money takes from one ledger position to another*, including the simulated outside world it briefly passes through.

This glossary is under active construction as `/grill-with-docs` resolves terms.

## Language

**Payment**:
A single instance of money moving via a **Rail**, in one direction, with one originator and one beneficiary. The canonical noun inside `payments-service`. Carries a **Lifecycle** (`PENDING → SETTLED` or `PENDING → RETURNED`), a **Rail** (`FPS`, `BOOK_TRANSFER`, future `BACS`/`CHAPS`/`SWIFT`/`SEPA`), an amount in minor units, a currency, and a destination identifier (sort + number + name for UK; IBAN/BIC for international).
_Avoid_: "Transfer" (the webapp's user-facing UX term — keep it for UI copy only); "transaction" (overloaded with `TransactionEntity`, which is the ledger-side grouping of two `LedgerEntryEntity` rows).

**Rail**:
A category of payment channel. v1 implements `FPS` and `BOOK_TRANSFER` properly; `BACS`, `CHAPS`, `SWIFT`, and `SEPA` exist as stubs that produce one-shot `SETTLED` payments with a banner explaining the real-world delay. Each non-book rail credits *inbound* funds through its own inbound `SUSPENSE` account; the *outbound* side reserves the payer's funds via a `ledger-service` **Hold** rather than a suspense account (see **Suspense account**).
_Avoid_: "network" (the operator of a rail is the **Scheme**, distinct from the rail itself); "channel" (collides with notification/channel terminology).

**FPS** (Faster Payments Service):
The UK's near-real-time retail payment rail, operated by Pay.UK. The default rail for customer-initiated pushes ≤£1M. In malai, FPS is the only non-book rail simulated end-to-end. Used for: send to another Malai customer, send to a non-Malai UK account, inbound from a Malai customer, inbound from a non-Malai UK account.
_Avoid_: "real-time payment" (broader umbrella that includes SEPA Instant, FedNow, etc. — say **FPS** when we mean FPS).

**BACS Direct Credit** (BACS-DC):
A UK batch credit rail operated by Bacs Payment Schemes Ltd, used for low-cost recurring credits like salary, pensions, and supplier payments. Multi-day cycle in the real world; in malai it lands as an instant `SETTLED` inbound payment with `rail = BACS_DIRECT_CREDIT`. v1 supports BACS-DC **inbound only** — the ambient-activity scheduler inside `malai-stub` fires recurring "salary" credits on a configured day-of-month, and a demonstrator can also trigger one-off inbound BACS credits via the stub admin endpoint. The customer-credit posting goes through `BACS_INBOUND_SUSPENSE`, parallel to `FPS_INBOUND_SUSPENSE`.
_Avoid_: "Direct Debit" (different scheme, opposite direction, mandate-based — owned by `direct-debit-service`); "BACS" alone (BACS is the *scheme operator* and runs both Direct Debit and Direct Credit — always say **BACS Direct Credit** or **BACS-DC** when we mean the credit rail).

**Book transfer**:
A movement of money between two accounts both held at malai, executed entirely inside our ledger with no scheme involvement. Used for own→own. Both legs settle atomically inside one DB transaction; no `PENDING` state, no suspense account, no return path. Distinct from sending to another Malai customer (which goes via **FPS** even though both endpoints are at the same bank — the rail is determined by the payment type, not the location of the endpoints).
_Avoid_: "internal transfer" (overloaded with "internal API call"); "wire" (US-only).

**Scheme**:
The operator of a **Rail** (Pay.UK for FPS, Bacs Payment Schemes Ltd for BACS, SWIFT for SWIFT, EPC for SEPA). In malai, each scheme is replaced by a **Stub** that exposes the scheme's HTTP API; the bank reaches each scheme through a per-rail **Gateway**. The stub is the seam where a real bank would wire in the production scheme integration.
_Avoid_: "rail" (the Scheme operates the Rail — they're not interchangeable); "network".

**Gateway**:
The bank's per-rail connector that bridges malai's internal **SQS** world and the external **Scheme**'s HTTP world. One module per rail — `fps-gateway`, `bacs-gateway`, `swift-gateway` — each bundled into `malai-api`. A gateway consumes the `command-*` **Queue**s `payments-service` produces, maps the canonical message onto a **scheme-shaped** HTTP request (debtor/creditor, pacs-like fields, reason codes — simplified, not full ISO 20022) and submits it to the external scheme; the scheme returns a synchronous `202` ack and later an asynchronous callback (settled/returned), on which the gateway produces the matching `event-*` Queue back to `payments-service`; for inbound it receives the scheme's webhook and produces the `event-received` Queue. Gateways translate *shape* but make no *decisions*: they are **dumb and stateless** — no settle-vs-return logic (that lives in the **Stub**), no database; correlation identifiers (`endToEndId`, `paymentId`) travel on the wire. The canonical-vs-scheme-shaped wire contracts are shared with the Stub via a common messages module. They take over the SQS role `malai-stub` used to play.
_Avoid_: "processor" (collides with `InboundPaymentProcessor` in `payments-service`, and isn't the term a bank uses); "adapter" (too generic); "service" unqualified.

**Stub** / **`malai-stub`**:
A top-level Maven module inside `malai-backend` that compiles to its own Spring Boot deployable, *separate from* `malai-api`. It impersonates the outside world for demo purposes: it exposes the HTTP APIs of the external schemes (FPS, BACS, SWIFT) — rail submit/ack endpoints, the settle/return and inbound callbacks it fires back at the **Gateway**s, CoP via Pay.UK, Mambu-shaped ledger APIs later. It **no longer touches SQS**; the per-rail Gateways own all SQS traffic. The stub remains the *only* place where rail-specific simulation logic lives — settle-vs-return decisions, magic accounts, armed returns, CoP-override→return, inbound generation — so `payments-service` itself still sees the world only as SQS message channels, exactly as it would in production. The stub is intentionally *not* a dependency of `malai-api`: the production deploy unit has no compile-time link to the simulation code.
_Avoid_: "mock" (mocks are unit-test fixtures inside the same process); "fake" (overloaded); "simulator" without qualification (we say **Stub** for the deployable, "simulator logic" for what it does internally).

**Queue**:
An SQS queue used to exchange messages between `payments-service` and the per-rail **Gateway**. Named `<domain>-<component>-<type>-<event>.fifo` — the dotted convention in `docs/queues-naming-conventions.md` with dots replaced by hyphens (SQS forbids dots mid-name) and the AWS-mandated `.fifo` suffix. **Type** encodes perspective: a `command-*` queue carries an instruction the bank issues *to* the rail (`payments-service` → gateway); an `event-*` queue carries a notification the rail sends *back* (gateway → `payments-service`). SQS now stops at the Gateway — beyond it, the Gateway talks HTTP to the external **Scheme**. v1 queues: `payments-fps-command-submit`, `payments-fps-command-return` (bank → stub); `payments-fps-event-settled`, `payments-fps-event-returned`, `payments-fps-event-received` (stub → bank); `payments-swift-command-submit`, `payments-swift-event-settled`; `payments-bacs-event-received` (each `.fifo`). Message shape matches what an ISO 20022-ish scheme integration would carry: end-to-end id, amount, currency, debtor/creditor identifiers, return reason codes. Provisioned by the application on startup: a shared provisioner in `malai-commons` create-if-absent's every queue and its paired `-dlq.fifo` (uniform `maxReceiveCount=3`) from a catalog held in `malai-payments-messages` — the same mechanism against Localstack (local/e2e) and real SQS (AWS), not Terraform and not the init script.
_Avoid_: "notify" for the inbound-arrival queue (the resolved term is **received** — `event-received`).

**Suspense account** (inbound only, now a `ledger-service` **System account**):
A bank-owned ledger account that temporarily holds *inbound* funds between scheme receipt and customer credit — `FPS_INBOUND_SUSPENSE`, `BACS_INBOUND_SUSPENSE`. The **outbound** suspense accounts are **retired**: an in-flight outbound payment now places a `ledger-service` **Hold** on the payer instead of posting `customer → suspense`, and on settle posts `customer → SCHEME_SETTLEMENT` directly. These accounts are owned by `ledger-service` (System accounts), not by `account-service`.
_Avoid_: "clearing account" (clearing is a stage in the scheme cycle, not a place); "transit account"; "holding account"; using "suspense" for the *outbound* side (that's a **Hold** now).

**Settlement**:
The moment a **Payment** is irrevocably complete — for outbound, funds have left the bank via the **Scheme**; for inbound, funds have been credited to the customer's **Account**. For **FPS** this is effectively-instant (a few seconds in real life; the demo collapses or visibly delays this for teaching). Distinct from *submission* (the bank has sent the scheme message but the scheme hasn't confirmed).
_Avoid_: "completion", "finalisation".

**Return**:
The terminal lifecycle state when a **Payment** fails *after* submission to the **Scheme** — the recipient bank rejected it, the scheme rejected it, or it timed out. The customer is credited back as part of the return; the original `PENDING` payment moves to `RETURNED` and carries a **Return reason**.
_Avoid_: "refund" (a refund is a *new* payment in the opposite direction, customer-initiated; a return is rail-initiated reversal); "reversal" (ambiguous between return and recall).

**Return reason**:
A code attached to a `RETURNED` **Payment** explaining why it failed. v1 codes: `ACCOUNT_CLOSED`, `ACCOUNT_NAME_MISMATCH`, `RECIPIENT_BANK_UNAVAILABLE`, `BENEFICIARY_DECEASED`, `INVALID_ACCOUNT`. The simulator picks one; in a real bank this comes back in the scheme's ISO 20022 reject message.

**Lifecycle**:
The states a non-book **Payment** moves through: `PENDING → SETTLED` (happy path), `PENDING → RETURNED` (sad path), and — when **Screening** flags — `REFERRED → PENDING` (released by a favourable ruling) or `REFERRED → DECLINED` (adverse ruling). Terminal states (`SETTLED`, `RETURNED`, `DECLINED`) are irreversible at the payment level — a buyer asking "what about recalls?" gets "out of scope in v1, would be a new payment in the opposite direction." **Book transfer** payments skip `PENDING` and are created directly in `SETTLED`. **SWIFT** payments always settle in v1 (no return path).

**Screening**:
The pre-dispatch assessment of an outbound non-book **Payment** for fraud risk, recorded as a **Model Decision** in the MDR (`mdr-service/CONTEXT.md`).
_Avoid_: fraud check (broader), vetting, review (the human step is the MDR's **Human Disposition**)

**Decider**:
The thing **Screening** asks for a **Verdict** — a seam owned by the payments flow, so wiring a real model later replaces an implementation, not an architecture. v1's sole production implementation is the deterministic `malai-screening-rules` version `1`, recorded under that name because the audit plane must never claim an invocation that didn't happen.
_Avoid_: model (that's what a Decider may one day *be*), engine, rules engine, scorer

**Watchlist**:
The versioned list of counterparty names that **Screening** flags at any amount, pinned in the **Model Decision** by version and content hash so the list that fired is provable years later.
_Avoid_: blocklist, blacklist, sanctions list (a real bank's is a superset of ours)

**Screening mode**:
Whether a **Verdict** is allowed to move the flow. `SHADOW` (the default, and what a real-bank deployer inherits) records and proceeds regardless; `ENFORCE` (the demo posture) parks flagged payments in **Referral**. Recording happens identically in both — only the policy step branches.
_Avoid_: dry run, dark launch, enabled/disabled

**Referral** (state `REFERRED`):
The **Lifecycle** state of a **Payment** that **Screening** flagged — parked before **Scheme** submission with its ledger **Hold** intact, awaiting a Human Disposition. A referred payment is *born* `REFERRED`: initiation resolves the state before it returns, so the customer never sees a `PENDING` window on a payment that was never dispatched. The **Model Decision** that parked it is pinned on the payment row as an opaque pointer (never a foreign key into the MDR — ADR-0009), and the referral is audited as `PAYMENT_REFERRED` alongside the lifecycle family (`PAYMENT_INITIATED`, `PAYMENT_SETTLED`, `PAYMENT_RETURNED`).
_Avoid_: held (that's the ledger **Hold**), suspended, blocked, under review

**Declined** (state `DECLINED`):
The terminal **Lifecycle** state of a **Payment** the bank refused before **Scheme** submission — **Hold** released, nothing posted. Neither `REFERRED` nor `DECLINED` carries a customer-facing reason: a bank must not tip off a suspected fraudster, so the public contract surfaces the state and nothing more.
_Avoid_: rejected (an MDR ruling word), returned (post-submission failure), cancelled (customer-initiated; doesn't exist in v1)

**Resolver**:
The sweep that closes the enforcement loop. On a property-tunable few-second cadence it finds every **Payment** parked in **Referral**, presents each one's pinned **Model Decision** id to the MDR's **Gate**, and enacts whatever Actionable output the gate serves: `DECLINE` moves the payment to **Declined**, `APPROVE` releases it to `PENDING` and dispatches it to its **Rail**. The gate is the *only* thing it asks — it never reads Human Dispositions, so accept/edit/reject translation stays in the MDR (ADR-0009). Anything that is not an actionable `APPROVE` or `DECLINE` — a refused gate, an unparseable output, a gate outage — leaves the payment parked, which is the conservative-only invariant of ADR-0008 holding rather than a failure. Vacuous in `SHADOW` **Screening mode**, where nothing is ever `REFERRED`, so it needs no mode gating.
_Avoid_: retry job, reprocessor, release worker (it declines as often as it releases), poller (it enacts, not just observes)

**IBAN** (International Bank Account Number):
The international identifier for an account, used for cross-border payments. Per ISO 13616: country code (2 letters), check digits (2 digits), and a country-specific basic bank account number. Required for SWIFT-routed payments alongside the recipient bank's **BIC**.
_Avoid_: "international account number" (IBAN is the canonical name); never accept IBAN alone — pair with BIC.

**BIC** (Business Identifier Code, formerly SWIFT code):
The international identifier for a bank branch, used to route SWIFT messages. Per ISO 9362: 8 or 11 characters identifying institution, country, location, and optional branch. Required alongside an IBAN for SWIFT payments.
_Avoid_: "SWIFT code" (BIC is the standard's name); the rail is **SWIFT**, the identifier is **BIC**.

**Payment purpose**:
A short human-readable reason for an international payment, surfaced on SWIFT messages for regulatory and beneficiary-bank disclosure (e.g. "Rent", "Services", "Family support"). Optional on the wire in v1 but populated for any SWIFT request that carries the field. Persisted on the `Payment` row for international payments only.
_Avoid_: "memo" (overloaded with the UI-side `description`); "reference" (overloaded with standing-order references).

**FX (Foreign Exchange)**:
Customer accounts are GBP-only at malai (v1 invariant). When a customer initiates a SWIFT payment with a non-GBP destination `currency`, the bank debits the source GBP account `amountMinorUnits` exactly and displays the target-currency value via a fixed rate. The display rate is **not** the rate the recipient bank or the customer's real foreign-exchange provider would offer — it's a stub for the demo. No spread, no fees, no fluctuation.
_Avoid_: "exchange rate" (specifically the **display rate** in v1); never imply real liquidity.

**Available balance** / **Settled balance** → **moved to `ledger-service`.**
Superseded by the ledger's **Ledger balance** / **Held amount** / **Available balance** (see `ledger-service/CONTEXT.md`). Under the native-**Hold** model an in-flight outbound **Payment** places a **Hold** on the payer (reducing **Available balance**) and does *not* touch the **Ledger balance** until **Settlement**; the ledger is the sole computer of all three figures. The old "available = balance − sum of `PENDING` payments" derivation is retired — payments-service no longer computes balances.

## Relationships

- A **Payment** has exactly one **Rail** and one **Lifecycle** state.
- A non-book outbound **Payment** while `PENDING` has a `ledger-service` **Hold** on the payer and **no ledger postings**; on `SETTLED` it settles the hold into a `customer → SCHEME_SETTLEMENT` posting (plus a `SCHEME_SETTLEMENT → beneficiary` credit when the beneficiary is on-us); on `RETURNED` the hold is **released** and nothing is posted.
- A **Book transfer** **Payment** has exactly two ledger postings, both `SETTLED` at creation, with no hold (it settles instantly).
- The inbound suspense and `SCHEME_SETTLEMENT` accounts are `ledger-service` **System account**s, owned by no customer **AccountHolder**, and never appear in `GET /api/v1/accounts`.

## Invariants

- Every outbound **Payment** with state `PENDING` or `REFERRED` has exactly one active `ledger-service` **Hold** on the payer for its amount, and **no ledger entries**.
- A `RETURNED` or `DECLINED` outbound **Payment** has **no ledger entries** at all (its hold was released, never settled).
- A `REFERRED` **Payment** leaves `REFERRED` only under a Human Disposition's Actionable output (ADR-0008); an un-ruled verdict can park a payment, never move one.
- Every **Resolver** enactment fires only from `REFERRED` and re-reads the payment inside its own transaction, so a re-swept payment, a duplicate sweep, and a retried enactment are all no-ops. Idempotency is the state guard, not a dedup table.
- The **Resolver** calls the **Gate** *outside* the enacting transaction, for the same reason initiation screens before it audits: the enactment's audit write locks the global `chain_head` row, and a loopback call made while it is held deadlocks against the MDR's own audit write.
- The ledger sum-to-zero invariant (V5 deferred trigger) holds across every **Posting** the payment lifecycle drives.
- Every call a payment-initiating transaction makes out of the JVM — the account lookup, the ledger **Hold**, the **Screening** write to the MDR — happens *before* that transaction records its own audit event. Recording the audit event locks the single global `chain_head` row for the rest of the transaction, and the MDR's decision write audits on that same chain; a loopback call made after the lock is taken blocks until the caller's read timeout. Acceptance tests cannot see this (the MDR is a WireMock stub there) — the e2e slice is the guard.

## Flagged ambiguities

- **Payment vs Transfer**: the webapp's `CONTEXT.md` defines **Transfer** as "a one-off push of money from one of the Active AccountHolder's Accounts to a sort-code + account-number destination." That definition matches what `payments-service` calls a **Payment** restricted to outbound rails. Resolution: webapp keeps **Transfer** as the user-facing UX umbrella; backend uses **Payment** for the rail-side noun. The current `POST /api/v1/accounts/{accountId}/transfers` endpoint URL is grandfathered; new endpoints introduced for FPS/inbound use `payment` vocabulary.
- **Transaction**: the existing `TransactionEntity` is "a ledger-side grouping of two `LedgerEntryEntity` rows for one money movement." Under the new model a **Payment** owns 0–2 `TransactionEntity`s over its lifecycle (one for the `PENDING` postings, one for the `SETTLED`/`RETURNED` postings). Open question: do we collapse this to a single `TransactionEntity` with multiple ledger pairs, or keep two? To be grilled.
