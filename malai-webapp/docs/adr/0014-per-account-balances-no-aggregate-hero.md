# Per-account balances, no aggregate hero on AccountsPage

The AccountsPage shows each **Account**'s balance on its own row, and **does not** show a "Total balance" hero card aggregating the **Active AccountHolder**'s **Accounts** into a single figure. Quick actions and the accounts list sit directly under the greeting; there is no headline number above them.

This is the deliberately empty option. The comfortable one — a hero card carrying the sum of every Account's `amountMinorUnits` (which the earlier v1 cut shipped, backed by `useTotalBalance` parallel-fetching every balance) — was rejected after demo feedback flagged it as unfamiliar.

## Why no aggregate

The aggregate hero is **not the UK retail-banking convention.** A scan of the apps a bank decision-maker would compare us against:

- **Monzo** — shows the current account balance; pots have their own balances; no aggregate.
- **Starling** — current account balance; spaces (sub-accounts) have their own; no aggregate.
- **Chase UK** — account-by-account, no cross-account total.
- **Lloyds / Halifax / Barclays** — list of accounts each with their own balance; no aggregate.

In a demo where the **Core Value** is _"this looks like a real UK bank"_, an aggregate hero is an anti-signal: it reads as a personal-finance dashboard (Mint / Emma / Money Dashboard) rather than as a retail bank. Bank decision-makers comparing the demo to their own customer apps will notice the difference, and the difference reads as wrong.

A secondary cost the hero carried: it required `n+1` round-trips (one `/accounts/all`, then one `/accounts/{uid}/balance` per account) just to compute a number nobody asked for. Removing it removes that fan-out at first paint.

## Considered options

- **A — No aggregate hero (accepted).** Greeting → quick actions → accounts list. Each account row carries its own balance. Master visibility toggle lives as a small icon button next to the "Accounts" section heading so the privacy-in-public affordance survives.
- **B — Aggregate hero, on by default.** Matches PFM apps, not retail banks. Demo feedback: confusing.
- **C — Aggregate hero, hidden behind an opt-in toggle.** Doesn't satisfy the feedback (the complaint was that the _concept_ read wrong, not the _visibility_) and keeps the `n+1` balance fan-out alive whether or not it renders.

## When this ADR is superseded

If a future scenario surfaces a real reason to show a cross-account total — e.g. a Joint or Business **AccountHolder** with multiple sub-accounts where the aggregate genuinely reads as one customer-facing pot, or a "Savings goal" feature that summarises progress across pots — re-evaluate. Until that scenario lands, the answer is "per-account only."
