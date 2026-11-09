# Transfer insufficient-funds: inline form-level error, the only new carve-out from ADR-0007

The new Move-money write surface (`POST /accounts/{id}/transfers`, `POST /payees`, `DELETE /payees/{id}`, `POST /payees/confirmation-of-payee`) adopts the [ADR-0007](./0007-anti-enumeration-error-policy.md) generic-toast-by-default policy unchanged, with **one new carve-out**: a `409` from `POST /transfers` whose `MalaiCode` identifies **insufficient funds** is surfaced as an **inline form-level error** on the transfer form (_"Not enough money in this account."_). Every other error on every other new endpoint stays generic.

ADR-0007 explicitly reserved the right to add carve-outs when the disclosure is about _the signed-in user's own state, not a probe of someone else's_. The insufficient-funds case is exactly that: the user is authenticated, the source account is their own, and the disclosed fact is that _their_ balance can't cover _their_ requested transfer. No one else's state is enumerated. **The carve-out stands on that test alone** — see the correction below.

## Correction (payment-screening increment)

This ADR originally added a second justification: _"the disclosure is also already implicit in the source-account balance shown above the form, so the inline message restates a fact the screen already presents."_ **That was never true.** The transfer flow displays no balance anywhere — not in the source picker (`TransferPage.tsx` `SourceCard`, name only), not the amount step, not the confirm step (four rows: From, To, Amount, Reference). The claim described UI that does not exist and never did.

The carve-out itself is unaffected: it was always justified by the ADR-0007 "user's own state" test, which holds independently. Only the redundant second argument was false, and it is withdrawn.

It mattered because it was load-bearing in the wrong direction. **Screening** (ADR-0020) parks a flagged payment in `REFERRED` with its ledger **Hold** intact for as long as it takes a human to rule. While parked, the app showed the _ledger_ balance — which a Hold does not move — so a customer could read £20,000 on screen, try to send £15,000, and be told "Not enough money in this account." The inline message wasn't restating a visible fact; it was contradicting one. ADR-0021 fixes the underlying problem by leading every balance with the spendable figure.

## Concrete mapping per endpoint

| Endpoint                             | Status                                                    | Treatment                                                                                                                                    |
| ------------------------------------ | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /transfers`                    | `400`                                                     | Field-level inline errors (per ADR-0004).                                                                                                    |
| `POST /transfers`                    | `401`                                                     | Global signout handler.                                                                                                                      |
| `POST /transfers`                    | `403`                                                     | Generic toast.                                                                                                                               |
| `POST /transfers`                    | `404` (`DESTINATION_NOT_FOUND`)                           | Generic toast. _The backend's 404 framing is itself anti-enumeration; the UI completes the loop by not naming the input that failed._        |
| `POST /transfers`                    | `409` insufficient funds                                  | **Inline form-level error** — the carve-out.                                                                                                 |
| `POST /transfers`                    | `409` other (e.g. idempotency replay with different body) | Generic toast.                                                                                                                               |
| `POST /payees`                       | `400`                                                     | Field-level inline errors. Duplicate-payee 400 → generic toast (not "this payee already exists" — leaks holder state to a borrowed session). |
| `POST /payees/confirmation-of-payee` | `200` always                                              | The verdict (`MATCH` / `CLOSE_MATCH` / `NO_MATCH`) is the user-facing message; no error path.                                                |
| `DELETE /payees/{id}`                | `204`                                                     | Optimistic remove + success toast.                                                                                                           |
| `DELETE /payees/{id}`                | `404`                                                     | Treat as success. DELETE is idempotent; showing an error when the desired end-state is reached is bad UX, and nothing is disclosed.          |

## What this does NOT change

- ADR-0004 field-level inline validation is unchanged. Messages about the user's own input carry no enumeration signal.
- ADR-0005 / ADR-0007 login carve-out is unchanged. Two carve-outs total across the app: the login "_Incorrect email or password_" inline, and this insufficient-funds inline.
- The Sonner generic-toast pipeline remains the global error UX for everything that falls through.

## Considered options

- **Inline message naming the actual balance (e.g. "_You have £12.34, you tried to send £50_") — rejected for v1.** Requires the mutation handler to peek at the cached source-account balance; introduces a freshness assumption. (This option's original wording also claimed "the balance is on-screen above the form" — withdrawn, see the Correction above.) Defer until a real demo asks for it.
- **Generic toast for insufficient funds too — rejected.** Every UK retail bank tells the customer "insufficient funds"; surfacing that as an opaque toast would read as a malai-platform bug to a bank-exec demo viewer and undersell the platform's banking-fluency.
- **`MalaiCode` → copy translation table covering insufficient-funds plus other 409 variants — rejected.** ADR-0007 explicitly rejected translation tables ("the most enumeration-leakage" option). The carve-out is one code, picked deliberately because it passes the "user's own state" test; opening a table opens every other code to the same argument and erodes the policy.

## Note on the form's state after the inline error

The form stays interactive after the inline error fires — the user edits the amount and re-submits without having to dismiss anything. No full disable. A re-submit re-runs COPA only if the destination changed (it didn't, in this path), so the idempotency-key generated for the original `POST /transfers` is discarded and a new one is minted for the retry: same destination, different amount, different operation, different key.
