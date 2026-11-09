# Balance eye toggle (master on list, local on detail)

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

A new `useBalanceVisibility()` hook becomes the single source of truth for whether balances are masked in the UI. Implementation is a tiny Zustand slice using `persist` middleware backed by **`sessionStorage`** (not `localStorage` — see PRD Further Notes; the demo environment is a shared machine and the preference must not leak across viewers). Surface: `{ hidden: boolean, toggle(): void, setHidden(hidden: boolean): void }`. This hook is the only place in the codebase that touches `sessionStorage` for this preference.

`<AccountsPage>` gains a master toggle in its page-header row: an icon button next to `<h1>Your accounts</h1>` wired to `useBalanceVisibility().toggle`, with `aria-pressed` reflecting the hidden state and `aria-label` reading "Show balances" when `hidden` and "Hide balances" otherwise. Every `<AccountCard>` reads `hidden` from the store and passes it to `<BalanceText>`. There is no per-card eye toggle on the home page — list-level masking is one switch only.

`<AccountHeroCard>` reads the master state as its _default_ but layers a local component-level override. A second eye toggle on the hero card flips the local state; revealing on the hero **does not** propagate back to the master store. Returning to `/` after a local reveal must show the list still masked.

`<BalanceText>` renders `£·····` (five middle-dots `·`) when `hidden` is `true`. The dotted placeholder is sized to roughly the same width as a typical balance so card layout does not shift.

`useSignout`'s `onSuccess` is extended to call `useBalanceVisibility.setState({ hidden: false })` alongside the existing query clears, so the next sign-in starts with balances visible.

## Acceptance criteria

- [ ] `useBalanceVisibility()` exposes `{ hidden, toggle, setHidden }`.
- [ ] The store persists `hidden` to `sessionStorage` via Zustand `persist` middleware and reads it back on store init.
- [ ] `<AccountsPage>` renders an eye icon button next to its `<h1>`, with `aria-pressed` and `aria-label` reflecting the current state ("Show balances" when `hidden`, "Hide balances" otherwise).
- [ ] All cards on `<AccountsPage>` mask their balance to `£·····` when `hidden` is `true`.
- [ ] `<AccountHeroCard>` shows the masked state by default when `hidden` is `true`, with its own local toggle that reveals only the hero balance.
- [ ] Local reveal on the hero does not flip the master state — verified by navigating back to `/` and asserting the list is still masked.
- [ ] `useSignout`'s `onSuccess` resets `hidden` to `false`; the next sign-in starts with balances visible.
- [ ] Unit tests cover: initial `hidden = false`; `toggle()` flips it; `setHidden(true)` sets it; `sessionStorage` round-trip on a fresh store instance; reset behaviour after clear.
- [ ] E2E asserts the full round-trip: hide on `/` → click into a card → hero hidden by default → local reveal works → navigate back → list still hidden → sign out → sign in → balances visible.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

[02-balance-and-identifiers-fan-out.md](./02-balance-and-identifiers-fan-out.md)
