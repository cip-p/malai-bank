---
status: superseded by ADR-0002
---

# The outside world is simulated by `malai-stub` over SQS, not by in-process beans

> **Superseded by [ADR-0002](0002-per-rail-gateways-bridge-sqs-and-scheme-http.md).** The decision to talk to a deployable scheme stub (rather than in-process beans) stands; what changed is *where the SQS boundary sits*. SQS now stops at a per-rail **Gateway** inside `malai-api`, and `malai-stub` talks HTTP, not SQS. Read this ADR for the "why a separate deployable" rationale, ADR-0002 for the current wiring.

`payments-service` talks to the rails (FPS, BACS Direct Credit inbound, SWIFT/SEPA international outbound) through real SQS queues, even in local dev. The other side of those queues is a separate Spring Boot deployable — `malai-stub`, a top-level Maven module inside `malai-backend` that is *not* a dependency of `malai-api`. The stub also exposes synchronous HTTP endpoints for lookups that real rails handle synchronously (notably Confirmation of Payee — see `account-service` ADR-0001). The trade we're making is "more infrastructure for a demo" in exchange for "the architecture a buyer sees is the architecture a real bank would run."

## Considered options

- **`malai-stub` Maven module, separate Spring Boot deployable, SQS over Localstack locally and real SQS in AWS** *(accepted)*. The buyer sees rail-shaped queues, scheme-message-shaped payloads, and a stub deployable that they can swap out for a real Pay.UK / SWIFT / scheme integration without touching `payments-service`. `malai-stub` is excluded from `malai-api`'s dependency graph so the production deploy unit has no compile-time link to simulation code.
- **In-process bean behind an interface, polled by `@Scheduled`** *(rejected)*. Far less infrastructure. Would have given ~80% of the architectural-credibility outcome at a fraction of the build cost. Rejected because the demo's headline pitch *is* "this is a real payments architecture," and an in-process simulator undermines that exactly where buyers look most carefully. The architectural seam (an interface called `FpsScheme`) is much weaker than a deployable on the other side of an actual queue.
- **In-process queue + worker** *(rejected)*. Same drawback as the bean option; demos "we have queues" without the queue actually being a queue.
- **`malai-stub` as a sibling repo, not a module inside `malai-backend`** *(rejected)*. Matched the workspace's existing multi-repo pattern (`malai-website-v1`, `malai-webapp-v1`, `malai-backend`, `malai-infra`). Rejected in favour of the in-repo module: faster code sharing of Kotlin types, single `mvn install` exercising both sides of the queue, simpler CI. The stub still deploys separately and has its own `CLAUDE.md` / `CONTEXT.md` (forthcoming).
- **WireMock-only simulation** *(rejected)*. Matches the existing test pattern in `*-test-support` but means the running demo can't exercise the return path because WireMock doesn't run outside tests. Demo-breaking.

## Lifecycle and queue shape

The FPS rail is modelled deep:

```
PENDING (customer debit + suspense credit committed in payments-service)
   │
   │  publish on fps.outbound.submit  ──▶  malai-stub consumes
   │                                       decides settle vs return based on:
   │                                         - magic-account convention in stub's directory
   │                                         - admin "arm next" override
   │                                         - auto-return when CoP verdict was overridden by the user
   │                                       publishes on fps.outbound.settled OR fps.outbound.returned
   ▼
SETTLED  (suspense debited, scheme-settlement system account credited)
   or
RETURNED (suspense debited, customer credited back; return_reason recorded)
```

Inbound FPS mirrors this:

```
malai-stub admin endpoint receives an inbound-payment request, OR ambient scheduler fires,
   │
   │  publish on fps.inbound.notify  ──▶  payments-service consumes
   │                                       looks up beneficiary by (sort, account) in account-service's directory
   │                                       if found: credit beneficiary via FPS_INBOUND_SUSPENSE; PENDING → SETTLED
   │                                       if not found: publish a return on fps.inbound.returned (back at the stub)
```

Stubbed rails (SWIFT outbound for international, BACS Direct Credit for inbound salary) ride the **same plumbing** with rail-specific simulated delays (e.g. `swift.outbound.*` settles ~30 simulated seconds after submit). They have no return path in v1. They share the `payments` table; international payments use additional optional columns (`iban`, `bic`, `payment_purpose`). FX for international displays a fixed target amount at send time; the customer's GBP account is debited the GBP amount; no fees, no spread, no multi-currency accounts.

CHAPS is dropped from v1 — high-value same-day is atypical for retail demos and adds no surface the buyer asks about.

## Suspense accounts

Each non-book rail has a pair of bank-owned suspense accounts (one outbound, one inbound) and a single `SCHEME_SETTLEMENT` system account that stands in for the bank's notional Nostro at Bank of England (or its overseas correspondent for SWIFT). Concretely, the seed introduces: `FPS_OUTBOUND_SUSPENSE`, `FPS_INBOUND_SUSPENSE`, `SWIFT_OUTBOUND_SUSPENSE`, `BACS_INBOUND_SUSPENSE`, and `SCHEME_SETTLEMENT`. They are `AccountType.SUSPENSE` rows owned by the System user introduced by V14, filtered out of `GET /api/v1/accounts`, and queryable for reconciliation. The ledger sum-to-zero invariant (V5 deferred trigger) is unchanged: every state transition writes a balanced pair of entries within one `transaction_id`.

## Demo-control surface

- **Outbound returns** are produced by the stub in three ways, layered: (1) **magic accounts** in the stub's fictional other-bank directory always return with a specific `Return reason` (e.g. "Jane Closed" at 60-00-99 → `ACCOUNT_CLOSED`); (2) `POST /demo/arm-return { reason }` on the stub arms the next outbound payment to return ad-hoc; (3) **auto-return on overridden CoP** — if the customer sent despite a `NO_MATCH` or `CLOSE_MATCH` warning, the stub returns with `ACCOUNT_NAME_MISMATCH`. The CoP-override→return loop is the most powerful single demonstration of why the rail's name-check exists — it surfaces the same anti-fraud signal end-to-end.
- **Inbound payments** are triggered by an admin endpoint on the stub (`POST /demo/inbound/fps` with originator + beneficiary + amount), publishing onto `fps.inbound.notify` blindly without checking whether the beneficiary exists in malai. `payments-service` handles the unknown-beneficiary case by publishing a return — modelling realistic "garbled inbound" flows. Ambient activity (e.g. simulated salary on the 25th) is layered on top via a `@Scheduled` job inside the stub.

## Consequences

- Local dev now needs **Localstack** (SQS) in addition to Postgres + WireMock. `start-docker-containers-local.sh` is extended to bring it up; CI gets a Localstack container in the test job. WireMock survives only for tests that pre-date this work.
- A new pair of client modules: `malai-clients/malai-stub-client` and `malai-stub-client-test-support`, matching the existing module-pair pattern. Used by `account-service` for the CoP HTTP call (see that service's ADR-0001) and potentially by other services later.
- `malai-infra` Terraform gains SQS queues and (probably) a new ECS service for `malai-stub`. Out of scope for the backend repo; recorded here so the `malai-infra` side has the canonical list.
- `malai-stub` gets its own `CLAUDE.md` and `CONTEXT.md` when its terms crystallise (current `CONTEXT-MAP.md` row reserves the slot).
- Idempotency now spans three layers: HTTP-layer `Idempotency-Key` on `POST /api/v1/payments` (existing), SQS message dedup on the consumer side of `fps.outbound.submit` (stub) and `fps.outbound.{settled,returned}` (payments-service), and DB-level state-machine guards (e.g. only `PENDING → SETTLED` transitions are allowed; re-settling a `SETTLED` payment is a no-op). The `payments` row's state column carries the state-machine guard; SQS dedup is by `payment_id` and message type.
- A buyer evaluating the architecture sees: queues that exist in real life, scheme-message-shaped payloads, a separate deployable on the other side of the rails, and a swap-in seam for the production scheme integration that doesn't touch `payments-service`.
