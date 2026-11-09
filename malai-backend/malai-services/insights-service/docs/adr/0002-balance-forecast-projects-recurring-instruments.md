# Balance forecast projects the account's recurring instruments, not its history

## Context

The client brief asked for a **Balance forecast** that surfaces an account's recurring payments and projects the end-of-month balance, and framed the recurring set as "repetitive transactions" detected from ~2 months of ledger history. But malai already models those payments as first-class instruments — `standing-order-service` (fixed amount, `Frequency`, `startDate`, bank-computed next occurrence) and `direct-debit-service` (mandate, amount, schedule) — whose `CONTEXT.md`s already own the terms **Next payment date** / **Next collection date**. The reference screenshots confirm the real bank drives the forecast off *instruments*: every row is tagged Standing order / Direct Debit. Detecting recurrence heuristically while holding the mandate table is the "real vs pretend machinery" seam ADR-0004 warns a bank technologist would spot, and it fights this context's rule that categorisation/analytics stay deterministic.

## Decision

The **Balance forecast** is a deterministic projection of the account's **active standing orders + active direct-debit mandates** onto the current calendar month — no history detection, no statistical guessing. Each instrument's occurrences are projected across the month with the shared `OccurrenceSchedule` math and split at today: **Completed occurrence**s (date ≤ today, already in the balance, display-only, shown at nominal amount) and **Expected occurrence**s (date > today). The forecast number is `availableBalance + Σ(Expected)` (signed minor units; the projection may go negative even though a `NO_OVERDRAFT` account's real balance cannot).

The computation lives in **`insights-service`**, whose charter broadens from "read model over the ledger" to "the analytics/forward-projection layer." insights stays **HTTP-client-free** (ADR-0001): `account-service` — the single internet-facing composer — fans out to `ledger-service` (available balance), `standing-order-service`, and `direct-debit-service` over new internal service-token, per-account read endpoints, then POSTs `{asOfDate, availableBalance, currency, active instruments}` to a new internal insights endpoint and passes the result through at `GET /api/v1/accounts/{accountUid}/forecast`. The read is scheduled-only: it fails **closed** (any downstream unavailable → error, like `GET /spending`), takes no `month` param (always "now"), and writes nothing (no idempotency key, no audit event).

v1 introduces **no arranged-overdraft facility** — malai models only a binary `NO_OVERDRAFT` guard — so the ⚠️ "into the red" state is simply `forecastBalance < 0`, derived by the webapp from the sign.

## Consequences

- A future Mambu swap of the ledger loses no forecast capability — it was never ledger state; the schedule truth lives in the instrument services.
- insights gains a second responsibility (spending breakdown + forecast) while keeping its "no outgoing HTTP, holds no money state" shape; the balance and instrument amounts are transient inputs, as entry amounts already are for spending-breakdown.
- Exhaustive, golden assertions live at the time-stubbed acceptance layer (insights unit + acceptance; account-service asserting every outgoing fan-out request); the real-stack `InsightsTest` asserts the four fan-out arrows plus clock-robust invariants, because the deployed `TimeService` is the real clock and the forecast has no `month` param to pin it.
- Populating **Completed** for a demo requires seeding instruments whose occurrence already fired this month *and* the matching backdated ledger posting, so `availableBalance` stays coherent with the displayed completed rows (registration-seeder path — every registered user gets forecast data).

## Considered and rejected

- **Detect recurring payments from ~2 months of ledger history** (the brief's literal framing) — non-deterministic, awkward to test, and a "pretend" seam when the authoritative mandate table already exists. There is no class of malai recurring payment (no card rail) that isn't already a standing order or direct debit, so history detection buys no coverage.
- **Compute the forecast in `account-service`** — avoids one internal hop, but scatters analytics into the internet-facing composer and tests the logic only through heavier acceptance paths instead of an isolated pure function; loses the coherent "insights = the money-management analytics layer" boundary.
- **Model an arranged overdraft** so the warning triggers at `forecastBalance < −limit` — net-new across the account entity, ledger guard, and contract, contradicting the v1 "no overdraft semantics" stance for a demo threshold the webapp can derive from the sign.
- **A settable clock (or `asOfDate` param) for deterministic e2e** — perturbs the SO/DD schedulers or pollutes the public contract; the acceptance layer already carries the deterministic coverage.
