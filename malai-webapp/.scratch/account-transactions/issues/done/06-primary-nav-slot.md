# Primary navigation slot in <AppShell>

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

`<AppShell>` gains a primary horizontal navigation row beneath the existing app bar. In v1 the row contains exactly one visible item: **Accounts**, linked to `/`. The nav is marked up as a `<ul>` of `<li>` items so future slots (Pay in the Payees phase; Statements / Direct Debits / Standing Orders in Phase 2) append without re-shaping markup.

Active state is indicated using existing tokenised styling (the same token surface that powers the existing focus ring). The active `<NavLink>` carries `aria-current="page"`.

On `md:` and below, the primary nav collapses into a hamburger menu using existing shadcn primitives — opening the menu reveals the same list of items.

The `Pay` slot is **not** rendered in v1. A demo viewer must never see a dead link. The markup is shaped so adding `<li><NavLink to="/pay">Pay</NavLink></li>` is the single change needed when the Payees phase ships.

This slice is purely additive — no other component or hook is modified. It can be implemented in parallel with any other slice.

## Acceptance criteria

- [ ] `<AppShell>` renders a primary nav row containing a single visible item: `<NavLink to="/">Accounts</NavLink>`.
- [ ] The active link carries `aria-current="page"` when on `/` or `/accounts/:uid`.
- [ ] On `md:` and below the primary nav collapses into a hamburger menu using existing shadcn primitives.
- [ ] No `Pay` link is rendered anywhere in v1.
- [ ] Existing axe spec passes on `/` and `/accounts/<known-uid>` with the new nav present (light + dark).
- [ ] E2E asserts the nav is visible on desktop and collapsed (hamburger) on mobile breakpoint; active state highlights on both `/` and `/accounts/:uid`.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

None — can start immediately.
