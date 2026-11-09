# Insights

The money-management analytics layer beside a swappable core: it interprets **Ledger entry** rows into **Category**s, owns user recategorisation, serves the spending breakdown, and projects a **Balance forecast** from the account's recurring instruments. It holds no money state — the ledger stays the uninterpreted book of record; insights is the interpretation and forward-projection layer a real bank runs beside a swappable core.

This glossary is under active construction as `/grill-with-docs` resolves terms.

## Language

**Category**:
One value of a closed ten-value taxonomy — `INCOME`, `GROCERIES`, `EATING_OUT`, `TRANSPORT`, `BILLS`, `HOUSING`, `SUBSCRIPTIONS`, `TRANSFERS`, `PAYMENTS`, `GENERAL` — assigned to every **Ledger entry** insights is asked about; never null, unmatched entries are `GENERAL`. A Kotlin enum in insights-service, a string enum on the wire (same convention as `Currency`).
_Avoid_: "tag", "label" (imply free-form); "spendingCategory" (Starling's name — we say **Category**).

**Override**:
A user's explicit **Category** choice for one **Ledger entry**, stored by `entryId`; always wins over rule-derived categorisation. The only state insights owns about an entry.
_Avoid_: "correction" (an Override is a preference, not a fix of an error); "recategorisation" as a noun (that's the act, the stored result is an **Override**).

**Rule**:
One ordered, sign-aware matcher in insights-service code mapping an entry's facts — `merchantName` keywords, then `description` keywords, then the transaction's caller-supplied `type` — to a **Category**; the rule set is code, not data, and ends in the `GENERAL` fallback.
_Avoid_: "rules engine" (it is an ordered list of matchers, not an engine); "ML"/"model" (categorisation is deterministic).

**Spending breakdown**:
The per-account, per-month aggregation insights produces: for each debit (money out) in the month, its **Category**'s running total, presented as positive magnitudes, plus a grand total. Only **Spending category**s appear.
_Avoid_: "budget" (no limits/targets in v1); "report"/"statement" (a statement is the ledger's period projection, distinct).

**Spending category**:
The subset of the taxonomy that counts as money spent — every **Category** except `INCOME` (credits, never a debit) and `TRANSFERS` (moving money to your own accounts isn't spending). Which categories are spending is insights' opinion, not the UI's.
_Avoid_: pushing this distinction into the webapp — the breakdown already excludes non-spending categories.

**Balance forecast**:
A per-account projection of where the spendable balance lands by the end of the current calendar month, given the recurring payments still to come: **available balance** minus every **Expected occurrence**. Deterministic and built only from the account's active **Recurring instrument**s — never inferred from past spending. Outgoing-only: income never appears (standing orders push out, direct debits pull out), consistent with the **Spending category** opinion.
_Avoid_: "prediction"/"estimate" (deterministic, not statistical); "budget" (no targets); reading it off ledger history.

**Recurring instrument**:
An active **Standing order** (defined in `standing-order-service`) or **Direct-debit mandate** (defined in `direct-debit-service`) treated as a forecast input — a known amount on a known schedule the forecast projects forward. The forecast treats the two uniformly; only the row's type label distinguishes them.
_Avoid_: "recurring transaction"/"repetitive transaction" (they imply detection from history — a **Recurring instrument** is a known mandate, not a detected pattern).

**Completed occurrence**:
A **Recurring instrument**'s **Occurrence** (defined in `standing-order-service`) dated on or before today within the forecast month; already reflected in the **available balance**, shown for context and never subtracted again.
_Avoid_: "paid"/"settled" (v1 projects it from the schedule at its nominal amount; it is not reconciled against the actual posting).

**Expected occurrence**:
A **Recurring instrument**'s **Occurrence** dated after today through month-end; these are the amounts subtracted from **available balance** to produce the **Balance forecast**.
_Avoid_: "pending" (a pending payment is a real in-flight ledger hold; an **Expected occurrence** is a projection that has not happened).

## Relationships

- An **Override** applies to exactly one **Ledger entry** (defined in `ledger-service`) and beats any **Rule**.
- A **Category** is assigned at read time — **Override** first, **Rule**s in order, `GENERAL` last; the **Ledger** never stores one.
- A **Balance forecast** covers exactly one **Account** for exactly one calendar month (always the current one).
- A **Balance forecast** is composed of **Occurrence**s projected from the account's active **Recurring instrument**s; every **Occurrence** is either a **Completed occurrence** (≤ today) or an **Expected occurrence** (> today), and only **Expected occurrence**s move the forecast number.

## Example dialogue

> **Dev:** "The customer flipped their Pret entry to `GROCERIES` — do we write that back to the ledger entry?"
> **Domain expert:** "Never. The ledger is uninterpreted and entries are immutable. Store an **Override** keyed by the entry id; every read applies Override first, rules second, `GENERAL` last."

## Flagged ambiguities

- `ledger_entries.category` (V5) was a dead column — nothing ever wrote it, yet it flowed to the public contract as always-null. Resolved: dropped from the ledger; **Category** is an insights concern, because a user-editable category on an immutable ledger entry contradicts "entries are never mutated".
- The client brief framed the forecast as "repetitive transactions" detected from ~2 months of history. Resolved: the forecast reads the account's active **Recurring instrument**s (standing orders + direct-debit mandates) directly — malai already holds the schedule truth, so there is no history detection and no statistical guessing; a **Recurring instrument** is a known mandate, not a detected pattern.
