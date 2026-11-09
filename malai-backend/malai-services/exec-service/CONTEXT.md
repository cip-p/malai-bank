# Exec metrics

The bank's headline numbers, composed for the Exec surface. Owns no tables and no money state: every figure is read from the context that owns it and assembled here (ADR-0011). What this context *does* own is the language — what each number counts, over what window, and what it means for one to be missing.

## Language

**Banking day**:
The window every Metric reports over: since midnight `Europe/London`. A UK bank's executive means the London day, not UTC.
_Avoid_: "today" unqualified in code or contract (ambiguous across timezones), "last 24 hours" (a rolling window is a different figure).

**Metric**:
One named headline number for the current Banking day, carrying either a value or an explicit statement that it is unavailable. Never a zero standing in for an absent number.
_Avoid_: "stat", "KPI", "widget" (the tile is the UI's word — see the webapp glossary's **Metric tile**).

**Unavailable** (metric):
The state of a Metric whose owning context could not be reached or did not answer within its timeout. A first-class contractual value, not an error and not a null to be guessed at — the surface renders it as an honest em-dash. Carries no reason: the client can act on none, and naming the sick service would leak internal topology.
_Avoid_: "failed", "error" (the response is a success; one number is simply not known), "null"/"missing".

**New customers**:
The Metric counting **AccountHolder**s created during the Banking day, all four types. Counted as holders rather than **User**s because a staff-only User has no holder and must never inflate the bank's customer count. Implemented: `account-holder-service`'s `GET /api/v1/internal/account-holders/created-count` counts the holder rows in the window, and the fan-out's `NewCustomersSource` reads it.
_Avoid_: "sign-ups"/"registrations" (registration creates a User *and* a holder; only the holder is a customer), "new users".

**Deposits**:
The Metric summing inbound **Payment**s that settled during the Banking day — money that entered the bank from outside. Excludes **Book transfer**s (both legs are internal, so counting them would let a customer inflate the bank's headline figure by moving their own money between their own accounts), card refunds (a reversal of spend, not a deposit), and anything not yet settled. A flow figure for the day, so a later **Return** does not retroactively reduce it. Implemented: `payments-service`'s `GET /api/v1/internal/payments/deposits-total` sums the settled, GBP, inbound payments that credited a Malai account in the window, and the fan-out's `DepositsSource` reads it. The "credited a Malai account" predicate is what carries the exclusions: a Book transfer is outbound and never counted; a direct-debit *collection* debits the customer and credits no account, so it has no beneficiary and drops out; a card refund is a ledger credit owned by card-service that never becomes a payment row, so it cannot reach the sum.
_Avoid_: "credits"/"money in" (would sweep in internal movements), "inflows", "balance" (a stock, not a flow).

**Cards issued**:
The Metric counting **Card**s issued during the Banking day. Counts issuance events, not surviving cards — a card cancelled the same day was still issued that day, and no bank's "cards issued today" silently decreases. Both variants count, and a replacement issued after a loss is an issuance like any other. Implemented: `card-service`'s `GET /api/v1/internal/cards/issued-count` counts the `cards` rows written in the window — filtering on `created_at` alone, never on status, which is what keeps a same-day cancellation in the figure — and the fan-out's `CardsIssuedSource` reads it.
_Avoid_: "active cards" (a stock, and a different number).

**Payments stopped**:
The Metric summing **Payment**s the bank refused after **Screening** review during the Banking day — sourced from the `DECLINED` **Lifecycle** state, which only an enacted adverse ruling produces. Deliberately *not* "fraud prevented": the decider is a deterministic rules engine, and a declined payment is money the bank stopped, not proven fraud. Implemented: `payments-service`'s `GET /api/v1/internal/payments/stopped-total` sums the `DECLINED`, GBP Payments in the window, and the fan-out's `PaymentsStoppedSource` reads it. Reading the `DECLINED` state rather than the MDR is what makes an *edit*-to-DECLINE count and a *reject* (still `REFERRED`) contribute nothing, without reimplementing the actionable-output translation ADR-0009 keeps in one place. The window keys on the payment's `created_at`, the only timestamp the row carries; in the demo a referral is declined in the same session it was raised, so that coincides with the day the money was stopped.
_Avoid_: "fraud prevented"/"fraud caught" (a claim the platform cannot substantiate), "rejected payments" (*reject* is an MDR ruling that leaves a payment parked and stops nothing).

**Metric currency**:
Every money-valued Metric carries its currency explicitly and reports GBP only in v1; non-GBP Payments are excluded from the figure and stated as excluded, never silently summed into it. Minor units of different currencies are not addable.
_Avoid_: a bare total with an assumed currency.

## Relationships

- A Metric belongs to exactly one Banking day and is either valued or **Unavailable**.
- Every Metric is derived from a context that owns the underlying facts; this context stores none of them.
- **Payments stopped** and the MDR's worklist are two readings of the same facts — a `DECLINED` Payment carries the `screening_decision_id` of the **Model Decision** that referred it, so drilling from the number into the Ops surface reconciles.

## Example dialogue

> **Dev:** The cards service timed out. Do we 500 the dashboard?
> **Domain expert:** Never. Three numbers are still true — serve them. The fourth comes back **Unavailable** and the tile shows an em-dash. A dashboard that refuses to render because one number is late is worse than one that admits what it doesn't know.
> **Dev:** And if a customer moves £5,000 from their current account to their savings?
> **Domain expert:** **Deposits** doesn't move. Nothing entered the bank — they shuffled their own money. If that counted, any customer could inflate the bank's headline figure from their phone.

## Flagged ambiguities

- "fraud prevented" — the client brief and both earlier PRDs named the fourth metric this way, and sourced it from "MDR reject rulings". Doubly wrong: a *reject* ruling leaves a payment parked and prevents nothing (it is *accept* of a DECLINE, or *edit* to one, that declines), and re-deriving the fact from MDR entries would reimplement the actionable-output translation that ADR-0009 keeps in one place. Resolved: the metric is **Payments stopped**, read from the `DECLINED` Lifecycle state.
- "new customers" — ambiguous between **User** and **AccountHolder**. Resolved as AccountHolder; a staff-only User is not a customer.
- "today" — resolved as **Banking day** (`Europe/London`), which sets a timezone convention the codebase did not previously have.
