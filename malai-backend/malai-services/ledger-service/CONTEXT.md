# Ledger

The bank's book of record: the double-entry store of money positions. It owns `transactions`, `ledger_entries`, the balance projections over them, and the bank's internal/system accounts. It is deliberately designed as a **swappable book-of-record** — the API is the seam where a real bank would wire in an external core ledger (Mambu) instead. It knows nothing about customers, account holders, rails, or payments; it moves money between **Ledger account**s on instruction and reports balances.

This glossary is under active construction as `/grill-with-docs` resolves terms.

## Language

**Ledger**:
The book of record — the authoritative double-entry store of every money movement and the balances projected from it. The "heart and core" of the banking system. In v1 it is malai's own implementation; the boundary is drawn so it could be replaced by an external ledger (Mambu) without callers noticing.
_Avoid_: "core banking" (broader — includes products, accounts, onboarding); "general ledger"/"GL" (accounting-flavoured; we say **Ledger**).

**Ledger account**:
An account *as the ledger sees it*: a registry row — `accountId` (UUID) + `currency` + `kind` (`CUSTOMER` | `SYSTEM`) + `guardPolicy` (`NO_OVERDRAFT` | `ALLOW_NEGATIVE` | `FLOORED`) + an optional `floorMinorUnits`. The guard policy resolves to a per-account **Available balance** floor: `NO_OVERDRAFT` ≡ floor 0, `ALLOW_NEGATIVE` ≡ unbounded, `FLOORED` ≡ the registered `floorMinorUnits` (signed minor units, typically negative — the credit-card shape, where the floor is −creditLimit and `floorMinorUnits` is required). The ledger holds **no** foreign key to `account-service`'s `accounts` table and knows nothing about account holders, ownership, sort codes, or onboarding status. A **Ledger account** must be *registered* before anything can post to it — `account-service` calls `registerLedgerAccount` when it creates a customer **Account**; **System account**s (suspense, scheme-settlement) are seeded. Posting to an unregistered account is a `4xx`, not silent money creation. This is the Mambu shape: open the account in the core before posting to it.
_Avoid_: "Account" unqualified (collides with the customer-facing **Account** owned by `account-service` — a ledger account is the same UUID seen through the ledger's narrower lens).

**Posting**:
The act of recording one **Transaction** — a balanced set of **Ledger entry** rows summing to zero — into the ledger, atomically and idempotently. The single core write the ledger exposes. A posting is idempotent on the caller-supplied **Transaction** id: replaying the same id is a no-op that returns the original result, so at-least-once HTTP delivery yields exactly-once money movement. A posting that would breach a guarded account's balance is rejected whole.
_Avoid_: "write"/"insert" (too low-level); "transfer" (a transfer is a payments-domain journey, a posting is the ledger mechanic beneath it).

**Transaction**:
The ledger's grouping unit: one money movement recorded as N **Ledger entry** rows whose signed `amount_minor_units` sum to zero. Carries the caller-supplied id (the idempotency key), a posting timestamp, and an opaque caller-supplied reference/type the ledger does not interpret.
_Avoid_: "payment" (a `payments-service` lifecycle concept that may map to several ledger transactions); "transaction" in the webapp UI sense (a single line on a statement — that's a **Ledger entry** to us).

**Ledger entry**:
One signed leg of a **Transaction**: an `(accountId, amount_minor_units)` pair, negative for a debit, positive for a credit, against one **Ledger account**. Balances are projected by summing entries; entries are never mutated.
_Avoid_: "line item"; "movement" (a movement is the whole **Transaction**).

**Hold**:
A reservation of an amount on a **Ledger account** that reduces its **Available balance** without moving its **Ledger balance** — money committed but not yet posted. The ledger owns holds outright. A hold is the native expression of "block amount, settle afterwards" and replaces the old outbound suspense account.
Lifecycle: `PLACED → SETTLED` or `PLACED → RELEASED` (no expiry in v1). A hold carries only `(accountId, amount)` — it does not know the destination. **Settle** is atomic post-and-release: in one ledger transaction the hold is marked `SETTLED`, a balanced **Posting** debiting the held account is recorded for the settle amount, and any remainder of the reservation is released, so **Available balance** never blips; idempotent on the hold id. Settle takes an optional settle amount ≤ the held amount (card-style partial capture — pay-at-pump authorises more than it presents); it defaults to the full held amount. Over-capture (settling for more than was held) is not supported. **Release** cancels the reservation and posts nothing — so a `RETURNED` outbound payment produces **zero ledger entries**.
_Avoid_: "authorization"/"auth" (card-network flavour); "suspense" (the old account-based mechanic a hold replaces on the outbound side); "lock" (a hold is a balance reservation, not a row lock).

**System account**:
A **Ledger account** the ledger owns outright rather than referencing from `account-service` — it belongs to no customer. v1 system accounts: the per-rail **inbound suspense** accounts (`FPS_INBOUND_SUSPENSE`, `BACS_INBOUND_SUSPENSE`) and `SCHEME_SETTLEMENT`. The *outbound* suspense accounts are retired — a **Hold** now reserves the payer's funds on the outbound side, so on settle an outbound payment posts `customer → SCHEME_SETTLEMENT` directly with no suspense hop.
_Avoid_: "GL account"; "internal account" (overloaded with "internal API call").

### Balances

**Ledger balance**:
`SUM(amount_minor_units)` over all of an account's **Ledger entry** rows — the bank's books, what is actually posted. Rendered as the account header "Balance" in the webapp. Under the **Hold** model an outbound payment in flight does **not** reduce this until it settles.
_Avoid_: "settled balance", "booked balance", "cleared balance" (all retired in favour of **Ledger balance**).

**Held amount**:
`SUM` of an account's active **Hold**s — money reserved but not yet posted.

**Available balance**:
**Ledger balance** − **Held amount** — what the customer can actually spend, and the figure the funds guard checks at post/hold time. Drops the instant a **Hold** is placed.
_Avoid_: deriving this from "pending payments" (the pre-hold definition; the source of truth is now the ledger's holds, not `payments-service`'s `PENDING` rows).

**Entries projection**:
The ledger's read of an account's posted **Ledger entry** rows, newest-first, each carrying a *running balance* (a window-function `SUM` over the account's full history). With no period it returns the most recent N entries (the webapp transaction list); with a `[periodStart, periodEnd)` it returns the in-period entries plus the **opening** (balance before the period start) and **closing** (balance before the period end) bounds — the statement. A held-but-unsettled payment has no **Ledger entry**, so it never appears here; a `RETURNED` outbound payment never appears. `account-service` no longer owns this projection — its balance/transactions/statement endpoints compose over the ledger client and wrap the result with account metadata (accountUid, currency).

## Relationships

- A **Ledger account** is referenced by, but not owned by, `account-service`'s **Account** for customer accounts; for system accounts (suspense, scheme-settlement) the ledger owns it outright.
- A **Transaction** has N (≥2) **Ledger entry** rows that sum to zero.
- A **Posting** records exactly one **Transaction**.
- The **Ledger** owns `transactions` and `ledger_entries`; balances are always projected (`SUM(amount_minor_units)`), never stored in a mutable column.

## Invariants

- **Sum-to-zero** per **Transaction** — enforced at the DB layer (V5 deferred constraint trigger).
- **No mutable balance column, ever** — every balance is `SUM(amount_minor_units)` projected on read.
- **Funds guard on available balance** — a **Posting** *or* a **Hold** that would take a guarded **Ledger account**'s **Available balance** below its permitted floor (0 for `NO_OVERDRAFT`, the registered `floorMinorUnits` for `FLOORED`, unbounded for `ALLOW_NEGATIVE`) is rejected atomically inside the operation (account locked, available checked, applied-or-rejected in one transaction). A credit is always accepted — the floor restricts the negative direction only, and a positive balance on a `FLOORED` account is legal. Callers do not pre-check; they handle the rejection. This is the ledger's invariant, not the caller's.
- **Idempotent posting** — a **Posting** is idempotent on the caller-supplied **Transaction** id; the `payments-service` lifecycle (the `payments` row) is the saga coordinator that drives retries, the ledger is the money authority, and the two reconcile by that shared id, not by a shared DB transaction.

## Flagged ambiguities

- **Account vs Ledger account** — resolved: the customer-facing **Account** (holder, identifiers, directory, CoP) stays in `account-service`; the **Ledger account** is the same `accountId` as an opaque money-position reference inside the ledger. No FK couples them; the future Mambu swap would not know `account_holder_id` either.
