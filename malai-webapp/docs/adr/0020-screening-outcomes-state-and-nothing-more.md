# Screening outcomes: the customer is told the state and nothing more

A **Payment** parked or refused by **Screening** shows the customer its state and nothing else. No reason, no code, no next step that narrows _why_. This is not a copy shortcut awaiting better words — it is the anti-tipping-off rule, and it is the same family of decision as ADR-0007's anti-enumeration policy: **never leak whether the bank suspects something.**

The contract enforces it upstream. `PaymentState` in the OpenAPI snapshot carries the rule in its own description — _"Deliberately no field explains why a payment is `REFERRED` or `DECLINED`. A bank must not tip off a suspected fraudster, so the customer-facing contract carries the state and nothing more."_ There is no `screeningReason` field to render even if we wanted one. This ADR records the webapp half: we don't manufacture one in copy either.

Concretely:

- **`REFERRED`** → "We're checking this payment", the amount and destination, and the fact that the money is set aside and hasn't left the source Account. Never "under review" (names the human step), never "held" (that's the ledger Hold), never "checking your details" (implies the customer erred).
- **`DECLINED`** → "We didn't make this payment", and the money is still in the source Account. Phrased as a **refusal**, never as a failure or an inability — no "couldn't", "unable to", "failed". A decline is a decision the bank made, and `CONTEXT.md` already reserved that distinction for the card rows.
- **The word "declined" never appears in the customer-facing payment copy.** Naming the refusal invites "why?", and we have decided we cannot answer. The state is `DECLINED` on the wire and in code; the screen says something else.

## Why the card rows are allowed a reason and this is not

`CONTEXT.md` defines a **Declined card row** that renders a visible **Decline reason** (`CARD_FROZEN`, `INSUFFICIENT_FUNDS`, `OVER_LIMIT`, `CARD_CANCELLED`). That looks like a direct contradiction. It isn't, and the distinction is the whole point:

Every card decline reason is **something the customer already knows or can act on** — they froze the card, they're out of money, they hit their limit. Telling them is helpful and reveals nothing the bank believes about them.

A screening decline is **the bank's suspicion about the customer or their counterparty**. Disclosing it — even as a hint, even as a category — coaches a genuine fraudster into their next attempt: try a smaller amount, try a different payee. That is precisely the behaviour tipping-off rules exist to prevent.

So the rule isn't "declines carry reasons" or "declines don't". It's: **disclose what the customer can act on; never disclose what the bank suspects.**

## Considered options

- **Quietly plausible, state only (accepted).** Reads as a real bank because it _is_ what a real bank does. The demo's legibility is carried by the **Ops surface** — the evaluator sends the payment, watches it go quiet, flips to Ops, and sees the Model Decision and the ruling that caused it in full. The customer screen stays in character; the joining-up is the demo script's job.
- **A generic softener — "for security reasons", "we can't complete this right now".** The most common real-world wording, and tempting. Rejected: "security" still names the category, which is the one bit of signal that matters. It buys warmth at the exact cost the rule exists to avoid.
- **Make the restraint conspicuous to the evaluator** — a visible marker that the silence is deliberate, in the spirit of the Gate panel's "the refusal is the feature". Rejected: the moment the Banking surface winks at the audience it stops being a bank and becomes a slide deck. The Ops surface is where the platform is allowed to explain itself.

## Enforcement

`PaymentOutcome.test.tsx` asserts the **absence** of the tipping-off vocabulary (fraud, suspicious, flag, watchlist, sanction, screen, blocked, compliance, risk) and of failure framing on the declined screen, rather than only asserting the copy that is there. A future contributor adding a helpful reason line — for consistency with the card rows, which is the obvious and wrong instinct — fails a test that says why.

## What this does NOT change

- **Returned payments keep their reason.** `formatReturnReason` and the `RETURNED` screen are untouched. A scheme return is a genuine delivery failure with a cause the customer can act on (wrong name, closed account) — the "couldn't be delivered" framing there is correct precisely because it _is_ an inability, not a refusal.
- **The Ops surface discloses everything.** The Model Decision's rendered prompt, retrieved-context refs with content hashes, raw output, verdict, and the full disposition thread stay fully visible to an ADMIN. This ADR constrains the Banking surface only.
