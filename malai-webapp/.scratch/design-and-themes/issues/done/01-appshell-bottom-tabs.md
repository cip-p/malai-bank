# AppShell: bottom tabs (mobile) + top nav (desktop) + Profile tab stub

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The shell shape that every authenticated page sits inside. After this slice lands, a signed-in **User** sees a thumb-reachable bottom tab bar on phones and the existing top nav on desktops; the third tab — **Profile** — exists as a stub destination so the IA is real even though its content lands later.

- The `AppShell` extracts a single nav config (three items: **Accounts**, **Move money**, **Profile**) and renders it twice at the Tailwind `md:` breakpoint: as the existing top nav at `≥md`, and as a fixed bottom tab bar below `md`. The bottom bar honours `env(safe-area-inset-bottom)` so labels and icons are not clipped on notched phones.
- The mobile-only `Collapsible` hamburger that today sits inside `PrimaryNav` is removed — bottom tabs replace it.
- The desktop header user-menu dropdown (Settings + Sign out) is rendered only at `≥md`. On mobile the dropdown is suppressed entirely; those affordances become reachable via the Profile tab. There must be one canonical home per breakpoint, not two.
- A new `/profile` route under `Authenticated` renders a stub page with `<h1>Profile</h1>` and "_Coming soon._". The Profile tab points at it.
- The active-route detection runs once over the shared config and drives both the top nav and the bottom tab bar; tab visual state uses background highlight + bold label + filled icon variant on the active item.
- Every authenticated page reserves bottom padding equal to the tab bar height + safe-area inset so fixed CTAs and toasts do not collide with the bar. Sonner toasts anchor above the tab bar on mobile.
- See [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md) for the rationale.

## Acceptance criteria

- [ ] Below the `md:` breakpoint the bottom tab bar is visible with three tabs (Accounts, Move money, Profile); the desktop top nav is not rendered.
- [ ] At `≥md:` the top nav is visible with two destinations (Accounts, Move money) plus the existing user-menu dropdown; the bottom tab bar is not rendered.
- [ ] The Profile tab is mobile-only — the desktop top nav has no Profile entry.
- [ ] Active tab indicator (background + filled icon + bold label) reflects the current URL on every navigation.
- [ ] Each tab item carries `aria-current="page"` when active.
- [ ] The bottom tab bar respects iOS / Android safe-area inset (no clipping on a simulated notch).
- [ ] All authenticated pages reserve bottom padding for the tab bar — no overlap with page content, fixed CTAs, or sonner toasts.
- [ ] `/profile` renders a stub destination on mobile; the desktop user-menu still contains Settings + Sign out.
- [ ] `AppShell.test.tsx` updated to cover: mobile renders three bottom tabs + no top nav; desktop renders top nav + no bottom tabs + Profile absent from desktop nav; active-tab indicator follows the URL.
- [ ] `tests/e2e/a11y.spec.ts` scans the new bottom-tab shell at a mobile viewport with no axe violations.
- [ ] No comments anywhere — including tests.

## Blocked by

None — can start immediately.
