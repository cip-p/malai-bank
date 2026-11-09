# Scaffold the Move-money section

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

The IA foundation for the entire Move-money feature, end-to-end and clickable. After this slice lands, a signed-in **User** can navigate the four-screen surface even though every screen is a placeholder.

- `AppShell.PrimaryNav` gains a single new link, `Move money`, sitting alongside `Accounts`. Active when the URL starts with `/move/`. Mirrors the existing `Accounts` link's `aria-current="page"` behaviour on both desktop and mobile.
- A new `MoveMoneyLayout` route renders the persistent **Move-money sub-nav** (four items: _Send money_ · _Payees_ · _Direct Debits_ · _Standing Orders_) plus `<Outlet />`. The sub-nav uses the same active-link styling as `PrimaryNav` and marks the active sub-route with `aria-current="page"`.
- Routes wired under `Authenticated`:
  - `/move` → redirect to `/move/send`
  - `/move/send` → placeholder page (`<h1>Send money</h1>` + body copy "_Coming soon._")
  - `/move/payees` → placeholder
  - `/move/direct-debits` → placeholder
  - `/move/standing-orders` → placeholder
- Mobile-nav addition mirrors `PrimaryNav`'s existing `Collapsible` pattern.

Placeholders exist purely so the IA is real and the sub-nav can be exercised. The four real screens land in slices 02–07.

## Acceptance criteria

- [ ] `Move money` link visible in `PrimaryNav` (desktop + mobile collapsible) for signed-in users; `aria-current="page"` set when on any `/move/*` route.
- [ ] `/move` (root) renders nothing of its own and immediately redirects to `/move/send`.
- [ ] All four sub-routes render under `MoveMoneyLayout`, which shows the Move-money sub-nav above the placeholder content.
- [ ] Sub-nav marks the currently-active sub-route with `aria-current="page"` and the same active styling used by `PrimaryNav`.
- [ ] Placeholder copy is consistent across all four pages and named with the **CONTEXT.md** terms (`Send money`, `Payees`, `Direct Debits`, `Standing Orders`).
- [ ] Playwright E2E spec covers: signed-in user clicks `Move money` from `/accounts`, lands at `/move/send`, navigates each of the other three sub-pages via sub-nav, returns to `/accounts` via `PrimaryNav`.
- [ ] `tests/e2e/a11y.spec.ts` extended to scan `/move/send`, `/move/payees`, `/move/direct-debits`, `/move/standing-orders` for axe violations.
- [ ] No comments anywhere — including tests.

## Blocked by

None — can start immediately.
