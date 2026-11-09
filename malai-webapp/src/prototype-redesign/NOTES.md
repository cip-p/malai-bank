# PROTOTYPE — throwaway, do not ship

**Question:** what should a from-scratch malai webapp design look like — new screens
and navigation flow allowed, login through accounts and payments?

**Where:** `/prototype/redesign` (DEV-only route, signed-in). Switch concepts with the
floating bar, `←`/`→` keys, or `?variant=a|b|c`. Each concept starts on its own login
screen (`?screen=login|home|pay`). All three run on real account/transaction/payee data
and respect the active Theme and Mode.

**Concepts:**

- `a` — **Ledger workspace.** Desktop-product shape: persistent sidebar (nav + accounts
  with inline balances), dense statement table with running balance, search, two-pane
  pay screen. Bets that the demo audience values an "operating a bank account" feel.
- `b` — **Pocket card deck.** Mobile-app-first: stacked brand cards, action chips,
  grouped activity feed, floating pill tab bar, keypad pay screen, full-bleed brand
  login. Bets that "feels like Monzo" is the strongest demo signal.
- `c` — **Atrium split-screen.** Editorial/private-bank: persistent brand panel with
  greeting and account list, statement-style content pane with hairline rules and
  oversized type, underlined-input pay form. Bets on brand-forward premium calm.

**Independent visual review (2026-06-11, fresh-context agent over screenshots):**

- Ranking by polish: **A > C > B**. A reads most like a production bank (ledger table
  with running balance verified internally consistent; survives halcyon dark). C is the
  most distinctive ("private bank" editorial; survives the linden retheme). B has the
  best consumer feel but the weakest desktop answer (centred phone column).
- Cross-cutting: the dev backend's seed data ("E2E DEEPLINK 206", "SarahE2E") is the
  single biggest credibility leak in any demo — a data fix, not a design fix.
- Known rough edges left as-is (prototype-grade): A login austere; A/B pay screens have
  vertical dead zones; halcyon-dark wordmark contrast in A's sidebar.

**Verdict:** _pending — fill in which concept (or which combination of pieces) wins,
then fold it into the real pages and delete this folder._
