# Cards

The bounded context that owns payment cards and the issuer-side processing behind them: the **Card** entity and its lifecycle, the **Card authorisation** flow (approve/decline, capture, reversal) for both debit and credit cards, and the credit-card product mechanics (credit limit, statement). It drives the ledger's **Hold** and **Posting** primitives directly — card spending does **not** flow through `payments-service`, whose **Payment** stays a push-via-rail concept.

This glossary is under active construction as `/grill-with-docs` resolves terms.

## Language

**Card**:
A payment instrument issued by malai against exactly one **Account** — carries a PAN, expiry, CVV, cardholder name, and a status. Comes in two variants: a **Debit card** attaches to a current-style Account (`PRIMARY`/`ADDITIONAL`); a **Credit card** attaches to a **Credit card account**. The variant determines which account an authorisation's hold lands on — the processing flow is otherwise identical.
_Avoid_: "card" for the webapp's UI panels (say "panel" or "tile" there); "plastic" (the Card outlives any physical form).

**Card authorisation**:
The issuer-side decision and lifecycle for one card transaction: a merchant's acquirer asks (via the **Scheme**) "approve this amount on this card?"; malai checks the card's status and available funds, places a ledger **Hold**, and answers approve or decline synchronously — the customer is standing at the till. Lifecycle: `AUTHORISED → SETTLED` (on **Presentment**) or `AUTHORISED → REVERSED`; a refused request is a **Decline** (persisted, zero balance effect). Authorisations do not expire in v1 (ledger Holds have no expiry; the Scheme stub always eventually presents or reverses). Owned entirely by `card-service` — it is **not** a `payments-service` **Payment**.
_Avoid_: "auth" in user-facing copy; "payment" (reserved for push-rail movements); "transaction" unqualified (collides with the ledger's **Transaction**).

**Presentment**:
The Scheme's later assertion of the captured amount for an approved **Card authorisation** — possibly less than was authorised (pay-at-pump), never more. On presentment the authorisation's Hold is settled for the presented amount (remainder released atomically) and a ledger posting with the merchant name is recorded; the purchase moves from pending to settled in the feed. Arrives asynchronously, unlike the authorisation itself.
_Avoid_: "capture" in code names (acquirer-side vocabulary; the issuer sees a presentment); "settlement" unqualified (collides with the payments-service term).

**Decline**:
A refused **Card authorisation** — card frozen or cancelled, or insufficient available funds / available credit. Persisted with a decline reason and shown in the account feed (visibly distinct, zero balance effect); no Hold is ever placed. Declines need no simulator magic: freeze the card or exceed the limit.
_Avoid_: "rejection" (reserved for validation errors); "failed payment".

**Refund** (merchant refund):
A credit back onto the card's Account, originated by the merchant through the **Scheme**, referencing a settled purchase and carrying the same merchant name. A new posting — it never rewrites the original entry.
_Avoid_: "return" (a `payments-service` rail concept); "reversal" (cancels an *unsettled* authorisation; a refund follows a *settled* one).

**Credit card account**:
An `account-service` **Account** with type `CREDIT_CARD`, ledger-backed like every account. Spending increases what the customer owes; "you owe" is the negated ledger balance. Available credit = **Credit limit** minus amount owed. Carries ordinary sort-code/account-number identifiers so repayments ride the existing book-transfer path, but is excluded from Confirmation of Payee. Created by the **Credit card application**, never at registration.
_Avoid_: "credit account" (ambiguous with any account in credit); "card account" (a debit card's account is just a current Account).

**Credit card application**:
The webapp flow by which an **AccountHolder** acquires a credit card: a short application form, an instant approval decision (no real underwriting — demo theatre), and the atomic creation of the **Credit card account** plus its **Card** with a fixed demo **Credit limit**. Debit cards are never applied for — they are auto-issued when a `PRIMARY`/`ADDITIONAL` Account is created.
_Avoid_: "onboarding" (reserved for registration); "underwriting" (implies a real decision process).

**Statement** (credit card):
The calendar-month view of a **Credit card account**: the cycle's entries plus three derived figures — **Statement balance** (amount owed at cycle close), **Minimum payment** (the greater of £5 or 2.5% of the statement balance), and the **Due date** (the 21st of the following month). Fully computed on read; nothing is persisted at cycle close in v1 (no interest, so cycle close is not an event).
_Avoid_: "bill" (US-flavoured); "invoice".

**Repayment**:
A customer-initiated payment *into* a **Credit card account**, reducing what is owed. Mechanically an ordinary book transfer from one of the holder's current-style Accounts to the credit card account's identifiers — `card-service` owns no money movement for it. The webapp's "Pay card" flow offers minimum / statement balance / custom presets.
_Avoid_: "refund" (merchant-initiated, comes through the **Scheme**); "payment" unqualified (collides with `payments-service` **Payment** — a Repayment *is* one, rail `BOOK_TRANSFER`).

**Credit limit**:
The maximum a **Credit card account** may owe. An authorisation that would push the amount owed past the limit is declined.
_Avoid_: "overdraft" (malai models no overdrafts; a credit limit is a product feature of the credit card, not a facility on a current account).

**Credit summary**:
The credit position of a **Credit card account** at a glance: **amount owed** (the negated ledger balance), **available credit** (the **Credit limit** plus the available balance, so active **Holds** reduce it immediately), and the **Credit limit** itself. Fully derived from the ledger on read — nothing is stored. Feeds the webapp's credit-account header and the "Pay card" presets.
_Avoid_: "balance" unqualified — a credit card has a ledger balance, an amount owed, and available credit; name which.

## Relationships

- A **Card** is issued against exactly one **Account**; an Account may have at most one active Card (v1). Card states: `ACTIVE ⇄ FROZEN → CANCELLED` (terminal); "report lost/stolen" cancels and auto-issues a replacement Card with a new PAN against the same Account. Cards are born `ACTIVE` — no ordering/activation step.
- A **Card authorisation** belongs to exactly one **Card** and, while `AUTHORISED`, holds exactly one active ledger **Hold** on the Card's Account.
- Settled purchases are plain **Ledger entries** (the presentment posting carries the merchant name) — the feed needs no card-service data for them. Pending (`AUTHORISED`) and **Decline** rows are card-service data, composed into the account feed server-side by `account-service`.
- A **reversal** releases the `AUTHORISED` authorisation's **Hold** (no posting) and moves it to `REVERSED`, so the pending row drops out of the composed feed and available balance is restored. A **Refund** is recorded once per scheme refund reference (the `card_refunds` idempotency record), so a redelivered refund event is a no-op; it posts a new credit **Ledger entry** on the referenced `SETTLED` purchase's Account carrying the original merchant name — a plain settled feed row, never a rewrite of the original entry. Both arrive asynchronously off the scheme-event queues, like **Presentment**.
- The credit-card statement mechanics (statement balance, minimum payment, due date) belong to this context; the underlying entries remain ledger **Ledger entries** on the **Credit card account**.
- The **Credit card application** and **Credit summary** surfaces are served by `account-service`, not `card-service`: `account-service` owns Account creation, identifiers, and the ledger registration that floors the account at −**Credit limit**, so the application atomically creates the **Credit card account** + its `CREDIT` **Card** (issued via the card client) + the floored ledger account, and the summary composes the ledger balances with the fixed limit. `card-service` still owns the **Card** and the credit **Statement** mechanics.
- The Sandbox card triggers (served by `sandbox-service`) resolve, ownership-scoped and server-side, a **Card**'s PAN and a settled **Card authorisation**'s scheme reference through two service-token internal reads (`GET /api/v1/internal/cards/{cardId}` and `GET /api/v1/internal/cards/authorisations/{authorisationId}`, both scoped by `accountHolderId`). The demonstrator never supplies the PAN or the scheme reference — the sandbox obtains them here and relays them to the scheme stub, so a sandbox-triggered purchase/refund is indistinguishable from an ambient one.

## Invariants

- A `FROZEN` or `CANCELLED` **Card** declines every authorisation request; freezing never affects already-approved authorisations or settled entries.
- Every `AUTHORISED` **Card authorisation** has exactly one active ledger **Hold**; a **Decline** has none; a `SETTLED` one has none (its hold was settled) and exactly one posting; a `REVERSED` one has none (its hold was released) and no posting.
- A **Presentment** never exceeds its authorisation's amount.
- The PAN never appears in logs or the audit chain (AuditRedactor), and is masked to the last four digits everywhere except the explicit customer reveal and the service-token internal card resolve (both trusted egress; the internal resolve keeps the full PAN server-side for the sandbox to originate scheme purchases — it never reaches the browser).

## Flagged ambiguities

- ~~Authorisation expiry~~ — resolved: no expiry in v1, consistent with no-expiry ledger Holds; the Scheme stub always eventually presents or reverses.
- ~~Capture amount vs authorised amount~~ — resolved: a **Presentment** may be for **less** than the authorised amount (pay-at-pump); the ledger's Hold settle takes the actual amount and atomically releases the remainder. Over-capture is not supported — the stub never presents more than it authorised.
