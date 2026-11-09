# Lead every balance with the spendable figure, and name what's on hold

Every account balance the customer sees is **`availableBalanceMinorUnits`** — ledger minus holds, the spendable figure. The **Account detail** screen additionally names the reserved amount ("£1,000.00 on hold") whenever `heldAmountMinorUnits > 0`. The ledger balance is no longer rendered anywhere in the Banking surface.

The contract has asked for this all along. `BalanceResponse.amountMinorUnits` carries the instruction in its own description: _"It moves only when money actually settles; an in-flight outbound payment no longer drops it (the reservation is a ledger Hold). **Lead the UI with `availableBalanceMinorUnits` instead.**"_ The webapp had been leading with the ledger balance since the first accounts screen, and `availableBalanceMinorUnits` and `heldAmountMinorUnits` had **zero** usages outside generated code.

## Why this surfaced now

It was always a latent bug, and **Screening** (ADR-0020) made it reachable.

An outbound FPS or SWIFT payment places a ledger Hold at initiation. Until now that Hold lasted about a second — the stub scheme settles almost immediately — so the window in which the displayed balance was wrong was too short for anyone to catch. A `REFERRED` payment parks with its Hold intact **until a human rules on it**, which in a demo is however long the presenter takes to switch surfaces and talk.

Inside that window the old behaviour was: the screen shows £20,000, the customer tries to send £15,000, and the form says _"Not enough money in this account."_ For an audience of banking technologists — people who will absolutely try exactly that — a balance that contradicts the bank's own error message is worse than any missing feature. It reads as the ledger being wrong, which is the single most damaging thing a core-banking demo can suggest.

## Why "on hold" discloses nothing

Naming the held amount is safe under ADR-0020's anti-tipping-off rule, and this was checked rather than assumed. **Every** in-flight outbound payment places a Hold — clean ones and screened ones alike. A held figure therefore carries no signal about whether screening flagged anything; it says only that money is committed and not yet gone. The customer learns _that_ their money is reserved, never _why_ a particular payment is taking longer.

## Considered options

- **Lead with available, name the hold on the detail screen (accepted).** Follows the contract's own instruction. Matches UK convention — Monzo and Starling lead with the spendable number; Lloyds and Halifax show "Balance" and "Available" together. Fixes the pre-existing `PENDING` window at the same time, at no extra cost.
- **Keep the ledger headline, add an "on hold" line beside it.** Cheaper and more literal about the book of record. Rejected: the headline number would still be one the customer cannot spend, which is the actual defect. An explanatory line under a wrong number does not make it right.
- **Show both figures everywhere, ledger and available.** Rejected for the list rows — two numbers per account turns a scannable list into a reconciliation exercise, and ADR-0014 already established that this app resists putting extra aggregate numbers in front of people. The detail screen is the right place for the breakdown, and that is where the held line lives.
- **Add a per-payment row to the transaction feed instead.** Would explain the specific missing money rather than just its total, but the feed composes ledger entries and card authorisations only (`account-service` holds no payments client), so it is a backend change in another repo. Worth doing on its own merits; it does not fix the balance, which is the load-bearing problem.

## Consequences

- ADR-0008's second justification for the insufficient-funds carve-out described a balance "shown above the form" that does not exist. Corrected in place; the carve-out stands on the ADR-0007 "user's own state" test alone.
- A `REFERRED` payment is still not individually reachable once the customer leaves the outcome screen — there is no payment list and no `/payments/:id` route. What this ADR guarantees is that the _money_ is always accounted for on screen. Making the individual payment findable remains open, and is most likely a backend feed change rather than a webapp route.
- The forecast screen already led with a spendable figure (`BalanceForecastResponse.availableBalanceMinorUnits`, a different field on a different endpoint). The Banking surface is now consistent with it rather than contradicting it.
