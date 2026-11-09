# Issue 04: Mobile sticky top bar with wordmark + per-page titles

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

Add a thin sticky mobile-only top bar to `AppShell` (rendered with `md:hidden`, `sticky top-0`, safe-area-inset-top padding) carrying two things and nothing else:

- The clickable `HeaderWordmark` SVG on the left, linking to `/`. Same SVG as the desktop header, so a Theme switch swaps it automatically.
- A per-page title string on the right.

Bottom tab bar remains the primary navigation per [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md) (amended in this round to record this new chrome layer). The user menu, the theme switcher, and the horizontal nav links **do not** appear in the mobile top bar — those affordances stay in the Profile tab on mobile to preserve the one-place-for-each-thing rule.

Page titles flow via a small mechanism — a Zustand store + `usePageTitle(title)` hook called by each authenticated page in an effect on mount, plus a `useActivePageTitle()` reader consumed by the AppShell mobile top bar. A page that doesn't call `usePageTitle` falls back to an empty title (the wordmark still renders alone). Title is a string only — no JSX, no icons, no actions in v1.

Every authenticated page declares its title:

- `AccountsPage` → "Accounts"
- `AccountDetailPage` → "Current account" (or the Account's `name` once known)
- `AccountNotFoundPage` → "Account"
- `MoveMoneyLayout` children: `TransferPage` → "Send money", `PayeesPage` → "Payees", `AddPayeePage` → "Add payee", `DirectDebitsPage` → "Direct Debits", `StandingOrdersPage` → "Standing orders"
- `ProfilePage` → "Profile"
- `SettingsPage` → "Settings"

The bar sits above the main content; `<main>`'s top padding accommodates it on mobile so content does not slide under the bar. The bottom padding already accommodating the bottom tab bar is unchanged.

The desktop chrome (≥ `md:` breakpoint) is unchanged by this work. ADR-0010 has already been amended during the grilling round; this issue does not touch documentation.

## Acceptance criteria

- [ ] AppShell renders a mobile-only sticky top bar (`md:hidden`, `sticky top-0`) on every authenticated page, with `pt-[env(safe-area-inset-top)]` so the wordmark/title clear notches.
- [ ] The wordmark on the left links to `/`; tapping it from any authenticated page navigates to Accounts.
- [ ] The right-hand title text changes per page according to the list above.
- [ ] The bar carries no user-menu, no theme-switcher, no horizontal nav links.
- [ ] The desktop `app-header` and `primary-nav-desktop` are visually and behaviourally unchanged on `md+`.
- [ ] A new `usePageTitle(title: string)` hook + backing store exists; each authenticated page calls it; `<AppShell>`'s mobile bar reads via a `useActivePageTitle()` consumer.
- [ ] Vitest unit coverage on `usePageTitle` + an integration test: render an `<AppShell>` around a child that calls `usePageTitle("Foo")`, assert the mobile bar shows "Foo" and links to `/`.
- [ ] The bar stays visible (`sticky`) when the user scrolls a long page (e.g. the transactions list).
- [ ] No regression on pages with fixed-bottom CTAs: the bottom safe-area accommodation that exists today is preserved.
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes; `npm run a11y` passes (the new bar does not break axe checks).

## Blocked by

None - can start immediately.
