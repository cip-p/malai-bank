# PRD: Send and receive money across UK and international rails

Status: ready-for-agent

## Problem Statement

A buyer evaluating malai expects to see a bank that can credibly *send* and *receive* money the way a real UK retail bank does — money flowing to and from another account I own, to and from another person at malai, to and from another person at a non-malai UK bank, and to and from an international account. Today the malai backend can only execute one of those eight flows: an authenticated customer can push money from one of their own Malai accounts to another own account or to one of six seeded fictional `EXTERNAL` merchants (Tesco, TfL, Octopus, Spotify, ACME Employer Ltd, Demo Payee 1). The other seven flows are absent.

Concretely:
- **Send to another Malai customer** is *blocked* in `TransfersService` at the destination-policy guard (`to.accountHolderId != from.accountHolderId && to.accountType != EXTERNAL` raises `TRANSFER_DESTINATION_NOT_FOUND`).
- **Send to a non-Malai UK account** is faked by the V14 seed — the only "external" accounts that exist are the six merchant rows in `accounts`. A real sort-code + account-number at NatWest / HSBC / Lloyds / Monzo resolves to `TRANSFER_DESTINATION_NOT_FOUND`.
- **Send internationally** has no representation at all — no IBAN/BIC handling, no FX, no SWIFT-shaped flow.
- **All four receive flows** have no representation — the system only ever debits/credits as a side-effect of an outbound transfer; nothing simulates payments arriving *from* the outside world.
- **No payment lifecycle**: V5's header comment is explicit — "single posted_at lifecycle. No pending/settled state." A buyer cannot see what a payment looks like in-flight, cannot see a return, cannot see suspense balances move, cannot see the rail behave differently from "money instantly disappears."
- **Confirmation of Payee** (per root ADR-0001) resolves against the bank's own directory and returns `NO_MATCH` for any genuinely external destination. The highest-value demo moment — CoP catching a name typo on an external payment — is unreachable.

The buyer's headline question is "this looks like a real bank, and the architecture beneath it is one we'd actually want to run." Without rails, scheme messaging, lifecycle, suspense, and returns, the backend cannot answer that question. The brief explicitly asks to "have a good understanding of what happens behind the scenes and then implement the following… SEND… RECEIVE… [with] some stubs and some queueing system in place for simulating third party systems."

## Solution

Build a new `payments-service` bounded context that owns money movement *between* accounts via real-world payment rails, and a new `malai-stub` Maven module (separate Spring Boot deployable, **not** bundled into `malai-api`) that impersonates the outside world over SQS queues and a CoP HTTP endpoint. UK Faster Payments (FPS) is built end-to-end (`PENDING → SETTLED` happy path, `PENDING → RETURNED` sad path with a `return_reason`, real SQS messaging via Localstack locally and real SQS in AWS); the other rails (SWIFT outbound for international, BACS Direct Credit inbound for salary) ride the *same* lifecycle and SQS plumbing with rail-specific simulated delays and no return path in v1. CHAPS is dropped from v1.

A new top-level `payments` table owns the `Payment` lifecycle and beneficiary identifiers; the existing `transactions` and `ledger_entries` primitives are unchanged and continue to enforce sum-to-zero per `transaction_id`. Each lifecycle transition appends a new transaction rather than mutating prior entries. Per-rail suspense accounts (`FPS_OUTBOUND_SUSPENSE`, `FPS_INBOUND_SUSPENSE`, `SWIFT_OUTBOUND_SUSPENSE`, `BACS_INBOUND_SUSPENSE`) and a single `SCHEME_SETTLEMENT` system account hold the bank's notional position while a payment is in-flight; they are `AccountType.SUSPENSE` rows owned by the System user, filtered out of `GET /api/v1/accounts`.

`Book Transfer` narrows to *own → own at the same Malai customer*. Every other customer-initiated push — including malai-customer-to-malai-customer — is a `Payment` with a `Rail`. The V14 per-merchant `EXTERNAL` rows in `accounts` are retired (clean-room latitude); the same fictional directory is relocated to `malai-stub`, where it powers both the federated CoP endpoint and the stub-side FPS settle-vs-return logic. `account-service`'s `ConfirmationOfPayeeService` learns to call out to `malai-stub` over HTTP on a local-directory miss, preserving root ADR-0001's Stance Y semantics and `0.85` anti-fishing threshold across the now-federated lookup.

The customer-facing API is `POST /api/v1/payments` (a new top-level resource). The legacy `POST /api/v1/accounts/{accountId}/transfers` remains as a thin shim delegating to `payments-service` until the webapp cuts over; the shim is deprecated, not preserved as a long-term path. Idempotency, audit, and ownership checks follow existing controller patterns; the audit chain (V7) is unchanged in shape and gains new action codes (`PAYMENT_INITIATED`, `PAYMENT_SETTLED`, `PAYMENT_RETURNED`, `INBOUND_PAYMENT_RECEIVED`).

Demo control is surfaced through `malai-stub`:
- An admin endpoint to trigger an inbound payment (`POST /demo/inbound/fps`) publishes blindly onto `fps.inbound.notify`. `payments-service` handles unknown beneficiaries by publishing a return-to-sender, exercising the realistic garbled-inbound path.
- An admin endpoint (`POST /demo/arm-return`) arms the next outbound FPS payment to return with a specified reason.
- A small set of "magic accounts" in the stub directory always return with specific reasons (`ACCOUNT_CLOSED`, `BENEFICIARY_DECEASED`, `RECIPIENT_BANK_UNAVAILABLE`).
- Outbound FPS payments where the customer overrode a CoP `NO_MATCH` or `CLOSE_MATCH` warning auto-return with `ACCOUNT_NAME_MISMATCH` — the most pointed end-to-end demonstration of why the rail's name check exists.
- A scheduled ambient-activity generator inside the stub seeds realistic recurring inbound (e.g. simulated salary on the 25th) for demos that need populated account histories.

The buyer reading the architecture sees: real SQS queues with ISO 20022-shaped payloads, a stub deployable on the other side that they can replace with a real Pay.UK / SWIFT integration, a suspense-account topology that matches how production banks' general ledgers actually work, a payment state machine that exposes the rail's seams, and a CoP integration that federates exactly the way real CoP does.

## User Stories

### Send flows (customer-facing)

1. As a malai customer with multiple Malai accounts, I want to move money from my Current to my Savings account, so that I can manage my own finances inside one bank. The move executes as a `Book Transfer` — synchronous, no `PENDING`, no scheme, no suspense — and both legs are visible on the next transaction list refresh.

2. As a malai customer, I want to send £100 to another Malai customer by typing their sort-code, account-number, and name, so that I can pay a friend who also banks with malai. The payment rides the FPS rail (not an on-us shortcut), shows briefly as `PENDING` with a "Processing" badge, and settles within ~1 simulated second.

3. As a malai customer, I want to send money to a non-Malai UK account (e.g. a NatWest account), so that I can pay anyone in the UK. CoP runs first against the federated directory, the payment rides FPS, and the recipient bank's stub records the inbound for demo inspection.

4. As a malai customer, I want to send money to an international account by IBAN + BIC + payment purpose, so that I can pay someone overseas. The payment rides the SWIFT-stub rail with a ~30-second simulated delay; the customer sees a fixed-rate FX conversion banner ("£100 → €115") at confirmation time; the customer's GBP account is debited the GBP amount; no fees.

5. As a malai customer about to send money to a recipient I have not saved as a payee, I want CoP to name-check the (sort, account-number, name) triple, so that I catch typos before money moves. CoP works whether the destination is a Malai customer, a Malai customer's seeded merchant account (legacy), or a non-Malai UK account in the stub directory.

6. As a malai customer who fat-fingers a recipient's name, I want CoP to return `CLOSE_MATCH` with the on-record name (when within the 0.85 similarity threshold), so I can self-correct without re-entering the whole pair. The threshold and verdict shape match root ADR-0001 regardless of whether the directory hit is malai-local or stub-federated.

7. As a malai customer who deliberately ignores a CoP `NO_MATCH` / `CLOSE_MATCH` warning and sends anyway, I want the rail to return the payment with `ACCOUNT_NAME_MISMATCH`, so that I learn the bank doesn't blindly trust me even when I override the front-end check. My account is credited back; the `Payment` row state moves `PENDING → RETURNED` with the reason recorded.

8. As a malai customer, I want to see my outbound `PENDING` payment in my transaction list with a distinct "Processing" badge, so that I know money has left my available balance but hasn't yet settled. The pending entry includes the destination, amount, and rail.

9. As a malai customer, I want my Available balance to reflect outbound pending (Settled balance minus the sum of my outbound `PENDING` payments), so that I cannot accidentally overspend by relying on the settled figure.

10. As a malai customer, I want my Settled balance to remain unchanged while a payment is `PENDING`, so that the bookkeeping figure (the one reconciliation reports against) is honest about what has actually moved.

11. As a malai customer whose payment fails after submission (e.g. sent to a magic-account that always returns `ACCOUNT_CLOSED`), I want a clear notification with the reason code, the original payment details, and the credited-back amount, so that I understand what happened.

### Receive flows (customer-facing)

12. As a malai customer, I want to receive money I sent myself from another Malai account I own, so that intra-customer book transfers credit my destination instantly with no `PENDING` window. The same book-transfer that debits my source account credits my destination atomically.

13. As a malai customer, I want to receive money another Malai customer has sent me via FPS, so that I see the inbound transaction in my list within ~1 simulated second of them initiating it. The inbound shows as `SETTLED` (no `PENDING` from the recipient's POV — receipt is recognised at settlement, not earlier).

14. As a malai customer, I want to receive money from a non-Malai UK account (an external inbound FPS payment), so that someone at NatWest can pay me. The demonstrator triggers it via the stub's admin endpoint; `payments-service` consumes the message, credits via `FPS_INBOUND_SUSPENSE`, and surfaces the `Transaction` in my account history with the originator's sort-code, account-number, and name visible.

15. As a malai customer, I want to receive money from an international account (inbound SWIFT-stub), so that someone overseas can pay me. The flow mirrors UK FPS inbound with the IBAN/BIC visible instead of sort+account.

16. As a malai customer who receives a regular salary, I want to see a recurring inbound credit show up on a configured day, so that the demo account looks like a real customer's account. The ambient-activity generator inside the stub publishes the inbound; my account history shows "ACME Employer Ltd — £2,500.00" on the simulated payday.

### Demo-control flows (sales-engineer-facing)

17. As a sales engineer running a live demo, I want a single admin endpoint on `malai-stub` to trigger an inbound payment of any amount to any malai sort+account, so that I can show "money landing" on cue. The endpoint validates only payload shape (no beneficiary existence check) and publishes onto `fps.inbound.notify`.

18. As a sales engineer, I want an admin endpoint to *arm* the next outbound FPS payment to return with a chosen reason, so that I can demo the return flow without memorising magic-account combinations. Subsequent outbounds settle normally.

19. As a sales engineer, I want a small set of "magic" accounts in the stub directory that always return with specific reasons (e.g. `ACCOUNT_CLOSED`, `BENEFICIARY_DECEASED`), so that I can run a reproducible scripted demo without arming the stub each time. The accounts are part of the seeded fictional directory and visible to CoP and the simulator alike.

20. As a sales engineer, I want a scheduled ambient-activity generator that seeds realistic recurring inbound (salary, recurring refund) on its own clock, so that a demo account looks lived-in even before I start clicking. The generator is configurable to switch off in environments where I want full control.

21. As a sales engineer demoing the CoP-override path, I want overriding a CoP warning and pressing send to *automatically* result in `ACCOUNT_NAME_MISMATCH` from the rail, so that the showcase educational moment (rail catches what front-end check warned about) works without setup. The stub detects the override flag on the outbound message.

### Architectural-credibility flows (buyer-facing)

22. As a buyer evaluating malai's architecture, I want to see real SQS queues with rail-shaped payloads (debtor identifiers, creditor identifiers, end-to-end ID, amount, currency, return reason codes), so that I can recognise the architecture matches what a production scheme integration would look like. Locally I see them in Localstack; in AWS I see them in real SQS.

23. As a buyer, I want `malai-stub` to be a *separate* Spring Boot deployable that is **not** bundled into `malai-api`, so that I can see the production deploy unit has no compile-time link to simulation code. Replacing the stub with a real Pay.UK integration is a deployment-time swap, not a code change in the bank.

24. As a buyer, I want suspense accounts in malai's `accounts` table that mirror how a real bank's general ledger handles in-flight funds, so that I can see the accounting model is honest rather than hand-waved. The suspense balance ticks up when a payment goes `PENDING`, ticks down when it settles or returns.

25. As a buyer, I want to see the `payments` table as the authoritative state machine for the lifecycle, with `transactions` + `ledger_entries` as the bookkeeping primitives, so that I can tell where lifecycle questions are answered versus where accounting questions are answered.

26. As a buyer, I want CoP to behave like real bilateral CoP (the originating bank queries the destination bank's directory), so that the demo represents the actual protocol rather than a bank's local cache. The malai → malai-stub HTTP call is the demo's stand-in for the Pay.UK CoP network.

27. As a buyer, I want the same payments architecture to handle FPS, SWIFT-stub, and BACS-Direct-Credit-stub identically (same `payments` table, same SQS plumbing, rail-specific timings only), so that I can see the design isn't FPS-only and would extend to the other UK and EU rails.

28. As a buyer, I want a documented swap-in path to a third-party core-banking ledger (e.g. Mambu), so that I can see where in the architecture a `malai-stub`-hosted Mambu-shaped HTTP API could be replaced by the real Mambu integration. The illustrative pattern is recorded in the payments-service `CONTEXT.md` and ADR.

### Developer experience

29. As a developer working on malai-backend, I want `start-docker-containers-local.sh` to bring up Localstack alongside Postgres + WireMock, so that I can run the full payments stack locally without AWS access. The existing wipe-on-rerun behaviour for `malai_test` extends to the Localstack queues.

30. As a developer, I want shared Kotlin data classes for the SQS payloads in a single `malai-payments-messages` module that both `payments-service` and `malai-stub` depend on, so that wire-contract drift is impossible. The module holds the messages, not the handlers.

31. As a developer, I want a `FpsScheme` interface in `payments-service` with a single SQS-publishing implementation, so that test code can stub the interface for unit-level tests and integration tests can swap to a real Localstack-backed implementation. The interface is the only seam the production scheme integration would touch.

32. As a developer, I want the audit chain (V7) to grow new action codes (`PAYMENT_INITIATED`, `PAYMENT_SETTLED`, `PAYMENT_RETURNED`, `INBOUND_PAYMENT_RECEIVED`) without structural changes, so that the existing append-only chain and global-chain-lock invariants continue to hold.

33. As a developer, I want the ledger sum-to-zero invariant (V5 deferred trigger) to remain unchanged, so that every state transition in the payment lifecycle results in a balanced pair of ledger entries within a transaction.

34. As a developer building the webapp's cutover path, I want `POST /api/v1/payments` to be the new canonical endpoint and `POST /api/v1/accounts/{accountId}/transfers` to remain temporarily as a delegating shim, so that I can migrate the webapp's existing `Transfer` flow in a follow-up PR without a same-release breaking change.

35. As a developer adding tests, I want a `WithPayments` interface in `malai-payments-client-test-support` that mirrors the existing `WithAccounts` / `WithPayees` pattern (request-parameters-as-arguments + response-builder), so that acceptance tests for cross-service flows follow the same shape.

### Maintainer experience

36. As a maintainer reading the repo in six months, I want an ADR at the root explaining the payments-service split, the `payments` table choice, the Book Transfer narrowing, and the new `/api/v1/payments` URL, so that I do not "fix" the bounded-context separation by merging payments back into account-service.

37. As a maintainer, I want an ADR in `payments-service/docs/adr/` explaining the SQS-plus-`malai-stub` topology and the rejected alternatives (in-process bean, in-process queue, real broker outside AWS, sibling-repo for the stub), so that I do not "simplify" the architecture by collapsing the stub into the same JVM.

38. As a maintainer, I want an ADR in `account-service/docs/adr/` recording that CoP federates to `malai-stub` on directory miss, so that I understand why CoP makes an outbound HTTP call.

39. As a maintainer, I want `CONTEXT-MAP.md` at the backend root to point at the new `payments-service` context, so that I can find the canonical glossary for `Payment`, `Rail`, `Suspense account`, `Settlement`, `Return`, `Available balance`, `Book Transfer`.

40. As a maintainer, I want the retired V14 per-merchant `EXTERNAL` accounts replaced cleanly by the new V14 (or new migration) seed introducing suspense accounts and the scheme-settlement system account, so that `accounts` only contains malai-owned accounts (customer + suspense).

## Implementation Decisions

### Module structure

- **New** `malai-services/payments-service/` — controllers (`PaymentsController`, plus an internal admin controller for cluster-internal calls if needed), services (`PaymentInitiator`, `PaymentLifecycleService`, `InboundPaymentProcessor`, `FpsScheme` interface + `SqsFpsScheme` implementation), entities (`PaymentEntity`), repositories (`PaymentRepository`). Acceptance tests live under `src/test/kotlin/com/malai/payments/acceptance/{happy,sad}/`.
- **New** `malai-clients/malai-payments-client/` + `malai-payments-client-test-support/` — paired client modules, used by any service that needs to call `payments-service`. Test-support exposes a `WithPayments` interface with response builders matching the `WithAccounts` / `WithPayees` shape.
- **New** `malai-clients/malai-payments-messages/` — pure Kotlin data classes for the SQS payloads (`FpsOutboundSubmit`, `FpsOutboundSettled`, `FpsOutboundReturned`, `FpsInboundNotify`, `FpsInboundReturned`, `SwiftOutboundSubmit`, `SwiftOutboundSettled`, `BacsInboundNotify`). No logic. Depended on by both `payments-service` and `malai-stub`.
- **New** `malai-stub/` — top-level Maven module (alongside `malai-api`, `malai-services`, etc.), a separate Spring Boot deployable. Owns `StubFpsScheme` (consumer of `fps.outbound.submit`), `StubInboundEmitter` (admin endpoints + ambient scheduler), `StubCopDirectory` (HTTP CoP endpoint), `StubFictionalDirectory` (the seeded fictional other-bank accounts). **Not** declared as a dependency of `malai-api`.
- **New** `malai-clients/malai-stub-client/` + `malai-stub-client-test-support/` — used by `account-service` for the CoP HTTP fallback. Test-support provides WireMock stubs for the stub's HTTP endpoints.
- **Modified** `malai-services/account-service/` — `TransfersService` shrinks to "book transfer only"; `ConfirmationOfPayeeService` learns the federated lookup; `TransfersController` becomes a delegator.
- **Modified** `malai-sql/` — new Flyway migration adds `payments` table, `AccountType.SUSPENSE`, suspense and scheme-settlement system accounts; V14 is rewritten in place (clean-room latitude) to drop the per-merchant EXTERNAL rows.
- **Modified** `malai-api/` — depends on `payments-service`; imports its `@Configuration`; root `pom.xml` registers the new modules in both `<modules>` and `<dependencyManagement>`.
- **Modified** `malai-commons/malai-commons/` — SQS client configuration via `MalaiClientConfiguration`-style `MalaiSqsConfigurationProperties`; Localstack-aware in dev/test profile.
- **Modified** `start-docker-containers-local.sh` — brings up Localstack (SQS + maybe S3 if needed for stub-side seed loading) alongside Postgres + WireMock; wipes queues on re-run consistent with the existing wipe-on-rerun semantics.
- **Modified** `malai-end-to-end-tests/` — adds scenarios for FPS happy path, CoP-override-then-return, inbound flow.

### Deep modules (encapsulated, simple interface, broadly testable)

- **`PaymentLifecycleService`** (in `payments-service`): the lifecycle state machine. Public surface: `transitionToSettled(paymentId)`, `transitionToReturned(paymentId, reason)`, `creditBeneficiary(inboundPayloadDto)`. Encapsulates state-machine guards (only `PENDING → SETTLED/RETURNED` allowed; re-applying a terminal transition is a no-op), the paired ledger postings (suspense ⇆ scheme-settlement on settle; suspense ⇆ customer on return), the audit event emission, and the suspense balance update. The single place lifecycle invariants are enforced.
- **`FpsScheme`** (interface in `payments-service`, `SqsFpsScheme` implementation): publishes outbound FPS submit messages. Public surface: `submit(payment)`. Encapsulates payload construction, dedupe key (`paymentId`), idempotent publishing, retry on transient SQS failure. The seam where a real Pay.UK integration would slot in.
- **`InboundPaymentProcessor`** (in `payments-service`): consumes `fps.inbound.notify`. Public surface: `process(FpsInboundNotify msg)`. Encapsulates SQS-message-level idempotency (dedup by `endToEndId`), beneficiary resolution against `AccountIdentifierRepository`, unknown-beneficiary return-to-sender publishing, and the `PENDING → SETTLED` transition that credits the beneficiary via `FPS_INBOUND_SUSPENSE`.
- **`CopVerdictService`** (in `account-service`): the existing CoP verdict logic, now extended. Public surface: `verdict(sort, account, name)`. Encapsulates the local directory lookup (against `account_identifiers ⨝ accounts` filtered to `AccountType ∈ {PRIMARY, ADDITIONAL, SAVINGS}` and `OnboardingStatus = ACTIVE`), the federated HTTP call to `malai-stub` on miss, the ADR-0001 Stance-Y name-match rules, and the `0.85` anti-fishing threshold. Returns a `CopVerdict` (`MATCH` / `CLOSE_MATCH(onRecordName)` / `NO_MATCH`).
- **`StubFpsScheme`** (in `malai-stub`): consumes `fps.outbound.submit`. Public surface: `onSubmit(FpsOutboundSubmit msg)`. Encapsulates the settle-vs-return decision (magic-account convention from `StubFictionalDirectory`, the arm-flag override, the CoP-override auto-mismatch when the outbound's `cop_verdict_override` is set) and publishes the appropriate outcome message onto `fps.outbound.settled` or `fps.outbound.returned`.

### Schema changes

**New `payments` table:**
```
payment_id           UUID PRIMARY KEY
rail                 VARCHAR(20)   NOT NULL    -- BOOK_TRANSFER | FPS | SWIFT | BACS_DIRECT_CREDIT
direction            VARCHAR(10)   NOT NULL    -- OUTBOUND | INBOUND
state                VARCHAR(15)   NOT NULL    -- PENDING | SETTLED | RETURNED
return_reason        VARCHAR(50)              -- nullable; set only when state = RETURNED
debtor_account_id    UUID                     -- nullable for INBOUND when originator is external
debtor_sort_code     VARCHAR(8)               -- when external originator
debtor_account_no    VARCHAR(8)
debtor_name          VARCHAR(255)
debtor_iban          VARCHAR(34)              -- international only
debtor_bic           VARCHAR(11)
beneficiary_account_id  UUID                  -- nullable for OUTBOUND when destination is external
beneficiary_sort_code   VARCHAR(8)
beneficiary_account_no  VARCHAR(8)
beneficiary_name        VARCHAR(255)
beneficiary_iban        VARCHAR(34)
beneficiary_bic         VARCHAR(11)
payment_purpose         VARCHAR(255)         -- international only
amount_minor_units      BIGINT       NOT NULL
currency                VARCHAR(3)   NOT NULL
description             VARCHAR(500)
end_to_end_id           UUID         NOT NULL UNIQUE  -- correlates with scheme messages
cop_verdict_override    BOOLEAN      NOT NULL DEFAULT FALSE  -- set when customer overrode CoP warning
created_at              TIMESTAMPTZ  NOT NULL
created_by_account_holder_id UUID
idempotency_key         TEXT
trace_id                VARCHAR(64)
```
plus indexes on `(beneficiary_account_id, state, created_at DESC)` and `(debtor_account_id, state, created_at DESC)`.

**`AccountType` enum gains `SUSPENSE`.** Suspense accounts are seeded by the rewritten V14 migration alongside the system user; the per-merchant `EXTERNAL` rows are removed (the same fictional data is relocated to `malai-stub`'s seed).

**No changes to `transactions` or `ledger_entries`.** The V5 deferred sum-to-zero trigger continues to hold per-transaction.

### API contract

- `POST /api/v1/payments` (new). Request body: `{ fromAccountId, rail?, toSortCode?, toAccountNumber?, toName?, toIban?, toBic?, paymentPurpose?, amountMinorUnits, currency, description, copVerdictOverride? }`. Response: `{ paymentId, state, rail, createdAt, endToEndId }`. The OpenAPI spec is updated *first* per the backend's "edit `openapi.yaml` first" rule; `oasdiff` against `main` will surface the addition.
- `POST /api/v1/accounts/{accountId}/transfers` (existing) — preserved as a thin delegator. Marked deprecated in OpenAPI; the same operation under `/api/v1/payments` is the canonical replacement.
- `POST /api/v1/confirmation-of-payee` (existing, per root ADR-0001) — unchanged in shape. Internal behaviour gains the federated `malai-stub` HTTP call on local miss.
- `GET /api/v1/payments/{paymentId}` — returns the payment in its current state (for the webapp to poll while `PENDING`; longer-term a webhook or WebSocket push would replace polling).

### `malai-stub` HTTP surface

- `POST /cop/lookup` — request `{ sortCode, accountNumber, name }`; response `{ verdict: MATCH | CLOSE_MATCH | NO_MATCH, onRecordName? }`. Same verdict shape as the existing CoP endpoint.
- `POST /demo/inbound/fps` — request `{ beneficiarySortCode, beneficiaryAccountNumber, beneficiaryName, originatorSortCode, originatorAccountNumber, originatorName, amountMinorUnits, currency, description }`. Publishes blindly onto `fps.inbound.notify`.
- `POST /demo/arm-return` — request `{ reason }`. Arms the next outbound FPS payment to return.

### SQS queues and message shapes

Queues (provisioned by Localstack locally, by `malai-infra` Terraform in AWS):
- `fps.outbound.submit` — `payments-service` → `malai-stub`
- `fps.outbound.settled` — `malai-stub` → `payments-service`
- `fps.outbound.returned` — `malai-stub` → `payments-service`
- `fps.inbound.notify` — `malai-stub` → `payments-service`
- `fps.inbound.returned` — `payments-service` → `malai-stub`
- `swift.outbound.submit` / `swift.outbound.settled` — same shape, simulated 30-second delay, no return path
- `bacs.inbound.notify` — same shape as FPS inbound

Message shapes live in `malai-clients/malai-payments-messages` as Kotlin data classes. Every message carries an `endToEndId` for correlation and a `messageId` for SQS dedup.

### Idempotency

Three layers:
- **HTTP layer**: `POST /api/v1/payments` carries an `Idempotency-Key` header handled by the existing `IdempotencyInterceptor`. `IdempotencyConfiguration.protectedPaths` is updated.
- **SQS layer**: consumers dedup by `(messageType, endToEndId)`; the dedup record is a small DB table updated within the same transaction as the lifecycle transition.
- **DB layer**: state-machine guards on `payments.state` reject illegal transitions; idempotent re-application of a terminal transition is a no-op (not an error).

### Audit chain

V7 chain unchanged in shape. New action codes:
- `PAYMENT_INITIATED` — when a `Payment` row is created (replacing `TRANSFER_EXECUTED` for non-book payments)
- `PAYMENT_SETTLED` — on `PENDING → SETTLED`
- `PAYMENT_RETURNED` — on `PENDING → RETURNED`, carries `return_reason` in `afterState`
- `INBOUND_PAYMENT_RECEIVED` — on inbound `SETTLED`
- `TRANSFER_EXECUTED` continues to be emitted by the book-transfer path (preserves existing audit history shape for the demoed scenario).

### Routing logic

The decision tree in `payments-service` when a request hits `POST /api/v1/payments`:
1. If `toSortCode` and `toAccountNumber` are present, look them up in `account-service`'s directory.
2. If found AND both source and destination belong to the same `account_holder_id` AND `rail` is not explicitly set — execute as `Book Transfer` (synchronous, `PENDING → SETTLED` collapsed to creation).
3. Otherwise — execute as an FPS `Payment` (debit source, credit `FPS_OUTBOUND_SUSPENSE`, publish `fps.outbound.submit`, return `PENDING`).
4. If `toIban` is present — execute as a SWIFT-stub `Payment` (debit source, credit `SWIFT_OUTBOUND_SUSPENSE`, publish `swift.outbound.submit`, return `PENDING`).

The "destination identifier shape determines the rail" rule is encapsulated in a `RailRouter` helper (shallow, but worth its own file for readability and test isolation).

## Testing Decisions

**What makes a good test in this codebase:**
- Exercises external behaviour — what an HTTP caller observes — not internal collaborators.
- Uses Kensa BDD (`given()` / `when()` / `then()` / `and()`) throughout.
- Asserts response payloads with Kacey: `responseEntity().containsProp(json { ... noExtraProps() })`. `noExtraProps()` is required at every level — top and nested.
- Asserts database state by comparing against the *full* expected entity (e.g. `theLastPaymentInTheDatabaseIs(paymentId, PaymentEntity(...))`). This forces tests to update when entities gain fields.
- Treats the controllers as the entry point — service-level acceptance tests extend `MalaiAcceptanceTest`, which composes `WithKotest`, `WithWiremock`, `WithApplicationContext`, `WithStubbedTime`, `WithCognitoJwks`, `WithCognitoJwtToken`, `WithServiceJwtToken`, `MalaiTestFixtures`.
- Uses `someAccessToken`, `someAccountUid`, `someCurrency` etc. from `MalaiTestFixtures` for shared constants.
- Auth via `HttpRequestBuilder.withJwtTokenForUserId(userId)` for the header path; cookie path tested via `.header("Cookie", "access_token=…")`.
- Splits happy-path tests from sad-path tests into `acceptance/happy/` and `acceptance/sad/` packages, matching existing convention.

**The user has explicitly stated all code we write must be tested.** Every module added by this PRD ships with tests at the appropriate level.

**Module-level test plan:**

- **`payments-service` acceptance tests (`@SpringTest`)** — controller-driven, cover:
  - `POST /api/v1/payments` happy paths: book transfer (own→own), FPS to another Malai customer, FPS to non-Malai UK (federated CoP), SWIFT stub to international.
  - Sad paths: insufficient funds, unknown source account, unknown destination after CoP miss, currency mismatch, source account ownership violation (404 not 403).
  - State-machine transitions driven by inbound SQS messages: `fps.outbound.settled` → `PENDING → SETTLED`; `fps.outbound.returned` → `PENDING → RETURNED` with reason recorded.
  - Inbound: `fps.inbound.notify` for a known beneficiary credits via suspense and emits `INBOUND_PAYMENT_RECEIVED` audit; `fps.inbound.notify` for an unknown beneficiary publishes `fps.inbound.returned`.
  - Prior art: `account-service`'s `TransfersControllerTest` (happy + sad), `AccountsControllerTest`, `ConfirmationOfPayeeControllerTest`.

- **`PaymentLifecycleService` acceptance tests** — invariant tests:
  - Sum-to-zero per `transaction_id` holds across every transition.
  - Suspense balance returns to zero after settle / return.
  - State guards: re-applying `PENDING → SETTLED` to a settled payment is a no-op; applying `SETTLED → RETURNED` raises.
  - Audit chain emits the expected sequence of action codes.
  - Database assertions use full-entity equality.

- **`FpsScheme` integration tests** — Localstack-backed (via testcontainers if not already running in the existing docker stack):
  - `submit(payment)` publishes exactly one message onto `fps.outbound.submit` with the expected payload shape and `endToEndId`.
  - Re-`submit`ing the same `paymentId` is a no-op (dedup at SQS layer + idempotency record).
  - Prior art: `malai-clients/malai-account-client`'s `AccountsOnboardingClientTest` for the request-construction pattern, applied to SQS instead of HTTP.

- **`InboundPaymentProcessor` integration tests**:
  - Consumes one `fps.inbound.notify`; resolves beneficiary; credits via suspense; emits audit; emits `INBOUND_PAYMENT_RECEIVED`.
  - Consumes one `fps.inbound.notify` with unknown beneficiary; publishes one `fps.inbound.returned`.
  - Duplicate messages (same `endToEndId`) are processed exactly once.

- **`CopVerdictService` acceptance tests (extends existing `ConfirmationOfPayeeControllerTest`)**:
  - Local hit returns the same verdict as today (verifies regression-free extension).
  - Local miss + stub hit returns the verdict from the stub (stub stubbed via `WithMalaiStub` test-support).
  - Local miss + stub miss returns `NO_MATCH`.
  - Anti-fishing threshold `0.85` applies identically on both paths.
  - Prior art: existing `ConfirmationOfPayeeControllerTest` in `account-service`.

- **`malai-stub` tests**:
  - `StubFpsScheme`: consumes `fps.outbound.submit`; settles for non-magic destinations; returns with the configured reason for magic accounts; returns `ACCOUNT_NAME_MISMATCH` when the message carries `copVerdictOverride = true`; honours an armed return-flag and clears it after one use.
  - `StubInboundEmitter`: `POST /demo/inbound/fps` publishes onto `fps.inbound.notify`; the ambient scheduler publishes on its configured cadence in profiles that enable it.
  - `StubCopDirectory`: `POST /cop/lookup` returns the verdict shape; lookup against fictional directory matches sort+account; threshold and verdict shape are identical to `account-service`'s logic.
  - These tests run with their own Spring context (`@MalaiClientSpringTest` shape or a new `@MalaiStubSpringTest` annotation) and don't share the `malai-api` boot.

- **Client modules** (`malai-payments-client`, `malai-stub-client`):
  - Each has `*-test-support` exposing a `With*` interface with WireMock stub methods and response builders.
  - Tests cover request construction (method, URL, headers, body) and response deserialisation.
  - Prior art: `malai-account-client` and `malai-account-holder-client`.

- **`malai-end-to-end-tests/`** — top-of-stack scenarios:
  - End-to-end FPS happy: customer A at malai sends £100 to customer B at malai; verify both account histories show the expected entries and the lifecycle traversed `PENDING → SETTLED`.
  - End-to-end FPS sad with CoP override: customer A sends to a stub-side account with a mismatched name despite CoP `NO_MATCH`; verify the return arrives and the customer is credited back.
  - End-to-end inbound: stub admin endpoint triggers an inbound; verify the recipient customer's account history reflects it.

- **`malai-payments-messages`** — pure data classes; no unit tests needed beyond what serialisation round-trip tests provide. A small `JsonRoundTripTest` per message type confirming `toJson(fromJson(x)) == x` is sufficient and follows the repo's `JsonSerialiser`-based pattern.

## Out of Scope

- **CHAPS** — high-value same-day. Not relevant to retail demo. Dropped.
- **Recalls** — sender-initiated reversal of a `SETTLED` payment. The buyer can ask "what about recalls?" — answer is "a new payment in the opposite direction, future scope." `Lifecycle` is deliberately two-state-terminal (`SETTLED`, `RETURNED`) with no `RECALLED`.
- **Multi-currency accounts** — customer accounts stay GBP-only. International payments display a fixed-rate FX conversion banner; the customer's GBP account is debited the GBP amount. No EUR or USD wallet balances; no FX pricing engine; no spread or margin.
- **Fees** — no fees on any rail in v1. International FX has no spread. The buyer can ask "where would fees go?" — answer is "future pricing service."
- **Real Mambu integration** — Mambu is illustrative only. `payments-service` keeps using `account-service`'s `LedgerEntryRepository` as the book of record in v1. The `malai-stub` deployable is architected to host a Mambu-shaped HTTP API later, but no Mambu-shaped endpoint is built in this PRD.
- **Bucket-based `CLOSE_MATCH`** that does not leak `onRecordName` (anti-fishing improvement noted in root ADR-0001) — same residual limitation. Out of scope; tracked for pre-production hardening.
- **Direct Debit creation / amendment / cancellation** — `direct-debit-service` remains read-only in v1, as the webapp `CONTEXT.md` already specifies. BACS-Direct-Credit *inbound* (salary lands) is in scope; BACS-Direct-Debit outbound is not.
- **Standing Order creation / amendment / cancellation** — same posture as Direct Debits, read-only.
- **Webapp UI work for the new rails** — the backend cutover is in scope; the webapp's `Send money` flow is updated only insofar as it currently calls `/api/v1/accounts/{accountId}/transfers` and should continue to work. Adding pending-payment badges, available-vs-settled-balance distinction in the UI, and the international flow are tracked separately under a follow-up webapp PRD.
- **`malai-infra` changes** — Terraform for the new SQS queues and the new ECS service for `malai-stub` are tracked in the `malai-infra` repo separately. This PRD assumes the queues are reachable when deployed; locally they are provisioned by Localstack via `start-docker-containers-local.sh`.
- **A dedicated demo-control UI on the stub** — optional polish noted during grilling. The admin HTTP endpoints are in scope; a wrapper UI is not.

## Further Notes

- **ADRs already exist** at `docs/adr/0002-payments-service-bounded-context-and-lifecycle-data-model.md`, `malai-services/payments-service/docs/adr/0001-outside-world-via-sqs-and-malai-stub.md`, and `malai-services/account-service/docs/adr/0001-confirmation-of-payee-federates-to-malai-stub-for-true-external-destinations.md`. Implementation should respect them; deviations should reopen the ADR rather than silently override.
- **Domain glossary** lives at `malai-services/payments-service/CONTEXT.md`. New code should use the canonical terms (`Payment`, `Rail`, `Suspense account`, `Settlement`, `Return`, `Return reason`, `Available balance`, `Settled balance`, `Book Transfer`, `Lifecycle`, `Scheme`, `Stub`, `Queue`) and avoid the listed synonyms.
- **Clean-room latitude** — the user has explicitly authorised dropping and redesigning schema (memory: `feedback_clean_room_schema`). V14 is being rewritten in place rather than preserved with shims; the per-merchant `EXTERNAL` rows are deleted from `accounts` and relocated to `malai-stub`. New Flyway migrations land naturally on top.
- **Cross-repo coordination** — the webapp regenerates its TypeScript client from `openapi.yaml` after this lands. The old `/transfers` operations remain, so the webapp does not need same-release changes; the migration to `/payments` is a follow-up. `oasdiff` will report the new endpoint and the `deprecated: true` flag on the old one — both expected.
- **Demo data** — the seeder (`malai-seeder` and `malai-registration-seeder`) does not need rail-aware changes in v1 because customer accounts are unchanged. The ambient-activity scheduler inside `malai-stub` provides the "lived-in account" feel rather than the seeder.
- **Audit and idempotency invariants** — the existing global chain lock and the existing `IdempotencyInterceptor` continue to hold; new SQS-layer dedup is additive, not a replacement. The chain-lock posture (single global lock acceptable for demo scale) is unchanged; the open critique on `malai-commons/idempotency` referenced in the workspace CLAUDE.md is also unchanged in scope.
- **The path to slicing this into issues** is naturally vertical: (1) schema + `payments` table + book-transfer migration; (2) FPS outbound happy path with Localstack SQS; (3) FPS outbound returns + CoP federation; (4) FPS inbound; (5) SWIFT outbound stub + BACS-DC inbound stub; (6) webapp cutover from `/transfers` to `/payments`. Running `/to-issues` against this PRD in a fresh session will produce that breakdown.
