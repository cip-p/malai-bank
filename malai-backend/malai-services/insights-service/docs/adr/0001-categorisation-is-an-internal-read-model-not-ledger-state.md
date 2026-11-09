# Spending categorisation is an internal read-model, not ledger state

## Context

Spending insights need a **Category** on every transaction, a per-account monthly **Spending breakdown**, and user recategorisation (an **Override**). `ledger_entries` shipped with a dead `category VARCHAR(50)` column (V5) that nothing ever wrote yet which flowed to the public `TransactionItemResponse.category` as always-null.

## Decision

Categorisation lives in a new internal `insights-service` (service-token only, like `ledger-service`), not on the ledger. It is a pure categoriser plus an `Override` store keyed by `entryId`, with **no outgoing HTTP clients**. `account-service` stays the single internet-facing surface: it verifies ownership, reads entries from `ledger-service`, and composes categories from `insights-service` — enriching the feed, serving `GET /accounts/{uid}/spending`, and forwarding `PUT`/`DELETE .../category` override writes (auditing them, since only account-service holds the `MalaiUser`). The dead `ledger_entries.category` column is **dropped**; the entries projection instead gains the transaction's caller-supplied `type` (additive) so **Rule**s can categorise sign- and type-aware. `merchant_name` stays on the ledger as uninterpreted narrative — it is a rule input, not a category.

## Consequences

- A user can recategorise an immutable ledger entry without violating "entries are never mutated" — the **Override** is insights state, the entry is untouched.
- A future Mambu swap of the ledger loses no categorisation; it was never ledger state.
- The feed **fails open**: if insights is unavailable, `account-service` returns entries with `category: null` and the statement still renders; only `GET /accounts/{uid}/spending` (which *is* the insights screen) fails closed.
- Two contexts, one direction: `account-service → insights-service`. No cycle, because insights never resolves ownership itself.

## Considered and rejected

- **Keep `category` on the ledger, mutate on recategorise** — breaks ledger immutability and would lose all categories on a Mambu swap; the exact contradiction a bank architect spots in the demo.
- **Internet-facing insights-service** — forces `insights-service → account-client` for ownership while `account-service → insights-client` for the feed: a two-way dependency between the contexts.
- **Flyway-seeded rules table** — no admin UI edits it in v1, so the flexibility is invisible while costing a repository, its tests, and migration churn for ~15 rules. Rules stay in code; a table is an additive change if a prospect ever needs runtime edits.
