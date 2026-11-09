# Design and themes: modern-banking polish, mobile-first shell, three-brand theme catalogue

Status: ready-for-agent

## Problem Statement

The webapp now covers the four real-bank primitives (Accounts, Transactions, Transfers, Payees, Direct Debits, Standing Orders) but reads as a competent shadcn-default app, not as a bank. A demo audience of bank executives walks away with "they got the data flowing" but not with "this is a banking app I'd license". The current `AppShell` is desktop-first with a hamburger on mobile, the lone `[data-theme="malai"]` block in `themes.css` is a generic muted blue, no font is loaded, there is no favicon (only a dummy placeholder in `public/`), accessibility commitments from [ADR-0006](../../docs/adr/0006-a11y-baseline.md) are partly delivered, and the theme-switcher feature implied by [ADR-0003](../../docs/adr/0003-theming-via-css-variables-and-token-discipline.md) has no UI.

The client brief asks for two things at once: **the app must look like a modern banking app and be mobile-first**, and **the User should be able to choose between different visual themes** — not dark/light, but distinct looks. Both halves move the demo forward: polish is the surface that bank decision-makers see first, and a working brand-rebrand demo (literally switching the app's identity in front of a prospect) is the most visceral version of malai's licensing pitch.

## Solution

A redesign across the whole app, anchored on the **Starling / Chase UK** visual idiom (restrained-premium, neutral palette with one accent, generous whitespace, soft radii, sans-serif but not playful). The work splits into foundation, shell, primitives, theme catalogue, page-level redesign, and a11y reinforcement.

- **Three-brand theme catalogue.** `themes.css` ships three named **Themes**: `malai` (the default — cream canvas / espresso ink / warm accent, leaning into the name's meaning), `halcyon` (navy fintech-challenger), `linden` (forest-green incumbent-heritage). Each defines the full design-token set including `--radius` and ships both light and dark **Modes**, both at WCAG AA contrast. The choice axes are orthogonal per [ADR-0003](../../docs/adr/0003-theming-via-css-variables-and-token-discipline.md): **Theme** = brand identity, **Mode** = dark/light. See [ADR-0011](../../docs/adr/0011-theme-catalogue-as-fictional-bank-brands.md) for the framing.
- **Header theme-switcher dropdown** on desktop (next to the user menu) and surfaced inside the **Profile tab** on mobile. Choice persists in `localStorage`. The header wordmark SVG swaps to the active theme's mark; the favicon stays `malai`-branded always.
- **`AppShell` rework** to bottom tabs (mobile) + top nav (desktop), three tabs — **Accounts**, **Move money**, **Profile**. Profile tab is mobile-only and aggregates Settings + Sign out + Theme. On desktop, those affordances live in the header user-menu dropdown as today. See [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md). Bottom-safe-area handling on every authenticated page so fixed CTAs (Send money, Add payee confirm) don't collide with the bar.
- **Inter** self-hosted via `@fontsource/inter` (variable), applied app-wide. Not themed — see [ADR-0012](../../docs/adr/0012-typography-app-wide-not-themed.md). A `<Money>` primitive applies `font-variant-numeric: tabular-nums` so columns of figures align.
- **PWA install set** — `manifest.webmanifest` plus `apple-touch-icon`, maskable Android icon, splash. Installable on iOS/Android; no service worker, no offline. See [ADR-0013](../../docs/adr/0013-pwa-manifest-no-service-worker.md).
- **Full layout redesign across all pages**, anchored on the **AccountsPage** — greeting + total-balance hero + 3 quick-action icons + per-Account card stack — and the **AccountDetailPage** date-grouped transaction list with category icons and a tap-to-open `TransactionDetailSheet`. Multi-step flow for **Transfer** and **AddPayee** reusing the RegisterLayout pattern, with a confirm step that gives the moment-of-weight a real bank's send carries.
- **Standard banking motion** — drawer slide on transaction detail, success-tick on confirm screens, balance count-up on first paint of the AccountsPage hero. All gated by `motion-safe` per [ADR-0006](../../docs/adr/0006-a11y-baseline.md) #3.
- **Accessibility** delivered at the ADR-0006 baseline (focus-on-h1 on route change, `aria-invalid` on form fields, `motion-safe` everywhere, per-theme AA contrast, sonner as the only live region) **plus** a new keyboard-only Playwright spec walking the demo path.

After this PRD lands, a bank exec watching the demo sees the app rebrand in real time (Theme switcher), uses it on their phone (PWA install + bottom tabs), reads it as a Starling/Chase-class polish (palette, type, motion, card hierarchy), and walks away registering the platform as fit for licensing.

## User Stories

### Shell + nav

1. As a signed-in **User** on a phone, I want a bottom tab bar with three destinations (Accounts, Move money, Profile), so that the primary destinations are thumb-reachable like every other UK banking app.
2. As a signed-in **User** on a phone, I want each bottom tab to clearly indicate the currently-active destination (background highlight + filled icon + bold label), so that I always know where I am.
3. As a signed-in **User** on a phone, I want the bottom tab bar to respect the iOS / Android safe area (notches, home indicators), so that the labels and icons are not clipped or obscured.
4. As a signed-in **User** on a desktop, I want a top horizontal nav with Accounts and Move money, with the user menu (Settings, Sign out, Theme switcher) in the top-right header dropdown, so that the desktop layout uses horizontal screen real-estate sensibly.
5. As a signed-in **User**, I want the `Move money` link in the bottom tabs / top nav to land me on `/move/send` (today's behaviour, preserved), so that the most common action is one tap away.
6. As a signed-in **User** on a phone, I want the **Profile tab** to be a destination page (not a popover or modal) aggregating Settings, Theme switcher, and Sign out, so that the affordances the desktop dropdown holds are still reachable.
7. As a signed-in **User** on a phone, I want the desktop header user-menu dropdown to be suppressed (not just hidden), so that there is one canonical home for Profile actions on mobile.
8. As a signed-in **User** on any page with a fixed CTA (Send money button on the Transfer screens, confirm button on AddPayee), I want the CTA to clear the bottom tab bar, so that it does not collide visually.
9. As a signed-in **User** on a tablet, I want the breakpoint to match Tailwind's `md:` (≥768px) so that the experience is predictable: phone-sized = bottom tabs, tablet+ = top nav.

### Theme + Mode switcher

10. As a signed-in **User**, I want a header theme switcher next to the user menu on desktop, opening a dropdown that lists the three Themes (Malai / Halcyon / Linden) with the active one marked, so that one click rebrands the app during a demo.
11. As a signed-in **User** on a phone, I want the same theme switcher surfaced inside the Profile tab, so that the rebrand demo works on mobile too without showing a header switcher that competes with the user menu.
12. As a signed-in **User**, I want each Theme entry in the switcher to show the brand's name and a colour swatch (its primary accent), so that I can preview before selecting.
13. As a signed-in **User**, I want selecting a Theme to apply it immediately — palette, `--radius`, and the header wordmark all swap — with no page reload, so that the rebrand reads as instant.
14. As a signed-in **User**, I want my Theme choice persisted to `localStorage`, so that it survives reloads in the same browser without a backend round-trip.
15. As a signed-in **User**, I want the dark/light **Mode** to be independently controllable from the active Theme, so that a Light Halcyon and a Dark Halcyon both render correctly.
16. As a signed-in **User** with `prefers-color-scheme: dark` at first paint, I want the **Mode** to default to dark on first visit (until I override it), so that the app respects my OS preference without surprise.
17. As a signed-in **User** who has not chosen a Theme on this browser, I want `malai` to be the default Theme on first paint, so that the demo lands on the deployment-branded look.
18. As an unauthenticated visitor on `/login`, `/register/*`, `/forgot-password`, etc., I want the active Theme + Mode to be honoured (no theme switcher visible, but the deployment-default styling still applied), so that the auth surface looks designed, not generic.
19. As a signed-in **User**, I want the favicon to remain the malai mark regardless of the active Theme, so that the browser tab identifies the deployment, not the in-app brand.

### Typography + money

20. As a signed-in **User**, I want all body and UI text in Inter, with weight axes tuned for the Starling/Chase-class look, so that the app reads as designed rather than as browser-default.
21. As a signed-in **User**, I want Inter loaded as a variable font from `@fontsource/inter` (self-hosted), so that there is no Google-Fonts third-party fetch and no FOIT.
22. As a signed-in **User**, I want every balance, transaction amount, and money figure rendered with tabular numerals, so that columns of amounts visually align down the page.
23. As a signed-in **User**, I want money figures to display as `£1,234.56` (UK locale, en-GB) with thousands separators and exactly two decimals, so that the demo reads as British and never `$1,234.56` or `1234.56`.
24. As a signed-in **User**, I want credit transactions to render with a leading `+` and a positive-tone colour token, debit transactions with a leading `−` and a negative-tone colour token, so that the direction is visible at a glance without parsing the sign of the number.

### AccountsPage hero + cards

25. As a signed-in **User** landing on `/`, I want a greeting using my first name (falling back to email per the existing `displayNameOf` helper), so that the home screen feels addressed to me.
26. As a signed-in **User** landing on `/`, I want a **Total balance** figure summed across all of the **Active AccountHolder**'s **Accounts**, in large display type, so that the headline number is the first thing I see.
27. As a signed-in **User** landing on `/`, I want the Total balance to animate from `£0.00` to its value on first paint (count-up), only when motion is allowed, so that the hero reads as alive without bothering users who set `prefers-reduced-motion: reduce`.
28. As a signed-in **User** landing on `/`, I want a row of three icon-and-label quick actions — **Send money** (→ /move/send), **Manage payees** (→ /move/payees), **Settings** (→ /settings on desktop, → Profile tab on mobile) — directly under the hero, so that the most common destinations are one tap from the home screen.
29. As a signed-in **User**, I want my **Accounts** below the quick-action row as a vertical card stack — one card per Account, tappable to `/accounts/:accountUid` — so that the structure mirrors the Starling/Chase template.
30. As a signed-in **User**, I want each Account card to show the Account's display name (e.g. "Current account"), the masked account number / sort code, the current balance (tabular nums), and the most recent Transaction's counterparty + signed amount as a one-line preview, so that I can scan my Accounts without drilling in.
31. As a signed-in **User** with exactly one Account, I want the card stack to render one card with no empty-state fallback, so that the layout is consistent across demo personas.

### AccountDetailPage + transaction list

32. As a signed-in **User** on `/accounts/:accountUid`, I want an account header with the Account name, balance (large, tabular), and the masked account number / sort code, so that the context of "which Account am I looking at" is anchored at the top.
33. As a signed-in **User** on `/accounts/:accountUid`, I want a prominent **Send money** CTA in the header that pre-fills the source account on the Transfer flow (preserving the existing `?from=` behaviour from the Move-money slice), so that the per-Account context carries through.
34. As a signed-in **User** on `/accounts/:accountUid`, I want my Transactions rendered as a date-grouped list with sticky date headers (`Today` / `Yesterday` / `5 June 2026`), so that scanning by recency is the primary scan pattern.
35. As a signed-in **User**, I want each transaction row to show: a category icon on the left (or a default icon when no category is set), the counterparty / merchant on the first line, the description on the second line in muted typography, and the signed amount right-aligned with tabular nums and direction-coloured, so that one row carries the same information a bank-statement row would.
36. As a signed-in **User**, I want to tap a transaction row to open a **`TransactionDetailSheet`** — a bottom sheet on mobile, side drawer on desktop — that shows the full detail including the running balance at that point, so that the row stays scannable but the power-user view is one tap away.
37. As a signed-in **User**, I want the sheet to be dismissible by tapping the backdrop, pressing Escape, or dragging down (mobile), and to return focus to the row I opened it from when closed, so that the keyboard / a11y flow is intact.
38. As a signed-in **User** with zero Transactions on an Account, I want a designed empty state (icon + headline + sub-copy) instead of a blank list, so that the empty case reads as deliberate.
39. As a signed-in **User**, I want the API's 100-row cap acknowledged with a footer note ("Showing the 100 most recent — older transactions are not yet available in malai") only when the list hits exactly 100 rows, so that the cap is honest without being noisy on smaller histories.

### Transfer + AddPayee flows (multi-step)

40. As a signed-in **User** starting a Transfer, I want each decision presented on its own screen — source Account, then destination, then amount + reference, then confirm — so that each step is a single decision with large touch targets.
41. As a signed-in **User** on any step of the Transfer flow, I want a clear back-step affordance and a visible progress indicator (e.g. `Step 2 of 4`), so that I always know where I am and how to retreat.
42. As a signed-in **User** advancing through Transfer steps, I want my entered values preserved when I go back and forward, so that I do not have to retype on a back-step correction.
43. As a signed-in **User** reaching the confirm step, I want a single screen summarising every decision — source Account + destination + amount + reference + COPA verdict — with a prominent **Send** CTA and a less-prominent **Back** option, so that the moment of weight that defines "real bank" is present.
44. As a signed-in **User** tapping **Send**, I want a success-tick animation followed by a success message and a CTA back to the source Account, so that the confirmation lands and the next-step is obvious. Animation is `motion-safe`.
45. As a signed-in **User** on the AddPayee flow, I want the same step pattern — Name → Sort code + Account number → CoP-check + confirm — so that the form pattern is consistent with Transfer.
46. As a signed-in **User**, I want both flows to live on top of a single reusable **`<StepFlow>`** primitive (extracted from RegisterLayout), so that adding a third multi-step flow later is a config not a copy.

### Brand wordmark + favicon + PWA

47. As a signed-in **User**, I want the AppShell header to render the active Theme's wordmark SVG — a stylised wordmark for malai / halcyon / linden — in the theme's accent colour, so that the rebrand demo is visible at the top of every screen.
48. As an unauthenticated visitor, I want the same wordmark surfaced on the `/login` and `/register/*` screens, so that the auth surface is branded, not just the authenticated surface.
49. As a visitor opening the app in a browser, I want the browser tab to show the malai favicon (the deployment brand) regardless of which Theme is active in-app, so that the tab identifies the deployment and does not flicker on theme change.
50. As a visitor on a phone or supported desktop, I want the browser to surface the "Add to Home Screen" / install prompt, so that I can install malai as if it were a native app.
51. As a User who installed malai to their home screen, I want the app to launch full-screen with the malai app icon and a splash screen, so that the installed experience reads as a native banking app and not as a web shortcut.

### Motion + a11y

52. As a User with `prefers-reduced-motion: reduce`, I want none of the bespoke animations (drawer slide, success-tick, balance count-up) to run, so that motion sensitivity is respected per ADR-0006 #3.
53. As a keyboard-only User, I want to navigate the entire demo path (sign in → Accounts → AccountDetail → open transaction sheet → close → Move money → Transfer 4 steps → confirm → sign out) using only Tab / Shift+Tab / Enter / Space / Escape, with a visible focus ring on every interactive element, so that the app passes a basic accessibility evaluation.
54. As a keyboard-only User on any page, I want the "Skip to main content" link to remain available at the top of the shell (existing `SkipToMainContent`), so that I can bypass the nav.
55. As a User on the AccountDetailPage opening the `TransactionDetailSheet` with a keyboard, I want focus trapped inside the sheet while open, and returned to the originating row on close, so that keyboard flow is not lost.
56. As a User on a multi-step flow, I want focus moved to the new step's `<h1>` on every step change (the existing focus-on-route-change convention extended to in-page step changes), so that screen readers announce the new step automatically.
57. As a User on a screen reader, I want every interactive icon (quick-action icons, theme switcher, tab-bar items) to carry an accessible name (`aria-label` or visible text), so that the icons are not voiced as "graphic graphic graphic".

### Cross-cutting page polish

58. As a signed-in **User** visiting `SettingsPage`, I want it laid out in clean grouped sections (Profile / Security / Appearance) in the new design system, so that it reads as the same app as the AccountsPage.
59. As a signed-in **User** visiting `PayeesPage`, `DirectDebitsPage`, `StandingOrdersPage`, I want each list to use the same card-and-row treatment as the Transaction list (icon + primary + secondary + right-aligned secondary), so that all read-only lists in the app feel consistent.
60. As a signed-in **User** on any list page with zero results, I want a designed empty state (icon + headline + optional CTA), so that empty cases are not blank panels.
61. As a visitor on `LoginPage`, I want a first-impression treatment — wordmark, generous spacing, focused single-column form, prominent CTA — so that the entry screen reads as a designed bank, not as a form on a white background.
62. As a visitor on the Register flow, I want the existing step layout treated with the new design system (typography, spacing, radii) so that registration matches the rest of the app.
63. As a visitor on `ForgotPasswordPage`, `ConfirmForgotPasswordPage`, `ConfirmRegisterPage`, `SignInHelpPage`, I want the same auth-surface visual treatment as Login, so that the recovery flows read as part of the same product.

## Implementation Decisions

### Architecture: ADRs that scope this work

- [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md) defines the nav shell: bottom tabs (mobile) + top nav (desktop), Profile tab as the mobile-only home for Settings + Sign out + Theme.
- [ADR-0011](../../docs/adr/0011-theme-catalogue-as-fictional-bank-brands.md) defines the catalogue: three fictional bank brands (malai / halcyon / linden) with light + dark, palette + `--radius`, wordmarks.
- [ADR-0012](../../docs/adr/0012-typography-app-wide-not-themed.md) defines typography: Inter via `@fontsource/inter`, app-wide, not themed; tabular nums on money components.
- [ADR-0013](../../docs/adr/0013-pwa-manifest-no-service-worker.md) defines the PWA scope: manifest + icons + splash, no service worker, no offline shell.

These plug into the prior commitments unchanged: [ADR-0003](../../docs/adr/0003-theming-via-css-variables-and-token-discipline.md) (token discipline, ESLint enforcement on Tailwind palette literals), [ADR-0006](../../docs/adr/0006-a11y-baseline.md) (focus-on-h1, motion-safe, AA contrast per Theme, sonner-only live region).

### Foundation modules

- **`useTheme` hook + `<ThemeProvider>`** — a deep module exposing `{ theme, setTheme, themes }`. Active theme stored in `localStorage` under a known key; on mount, reads `localStorage` then falls back to `malai`; on every change, writes both `localStorage` and `document.documentElement.setAttribute('data-theme', theme)`. Independent of `next-themes`, which handles the Mode axis. Small surface, lots of side-effect discipline behind it.
- **Theme tokens in `src/styles/themes.css`** — three `[data-theme="..."]` blocks and three `[data-theme="..."].dark` blocks. Each defines the full shadcn token set including `--radius`. Existing `malai` block is refreshed (cream canvas, espresso ink, warm accent) — not deleted, replaced. `halcyon` and `linden` are new.
- **Typography setup** — install `@fontsource/inter` (variable). `--font-sans: 'Inter Variable', system-ui, ...` in `:root`. App-wide `font-family: var(--font-sans)` on `body`. A utility class `.tabular-nums` (or a `<Money>` component default) for money-bearing components.
- **PWA assets** — `public/manifest.webmanifest` with malai brand colours, `apple-touch-icon-180.png`, `icon-192.png` + `icon-512.png` (maskable variants), `favicon.svg`, splash. Linked from `index.html`. All malai-branded.

### Shell modules

- **`AppShell` rework** — extracts a single nav config (`[{ label: 'Accounts', to: '/', icon: ... }, ...]`) and renders it twice: as a top nav at `md:` and above, as a bottom tab bar below `md:`. The mobile bar is fixed to the viewport bottom and includes `env(safe-area-inset-bottom)` padding. Active-route detection lives in one place. The existing `SkipToMainContent` and `<main id="main" tabIndex={-1}>` patterns are preserved.
- **Header `ThemeSwitcher`** — a desktop-only dropdown using `radix-ui` `DropdownMenu` (already in deps), positioned next to the existing user menu. Each item shows the theme name, a colour swatch (its `--primary`), and a checkmark on the active item.
- **Per-theme `<HeaderWordmark>`** — looks up the active theme and renders the matching SVG (one per brand) coloured by the theme's accent token. Three SVGs live under `src/components/wordmarks/`.
- **`ProfilePage`** (mobile route) — new page at the `/profile` route mapped to the third bottom tab. Renders Settings entry, Theme switcher (re-used surface), Sign out. Desktop visiting `/profile` redirects to `/settings` to avoid two homes for the same surface.

### Reusable primitives (deep modules)

- **`<Money>`** — props: `{ minorUnits: number, currency?: 'GBP', signed?: 'always' | 'negative' | 'never' }`. Pure formatting, no state. Returns a `<span>` with `tabular-nums` and direction colour token. Internally uses `Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })`. Replaces all ad-hoc money rendering in existing pages.
- **`<Balance>`** — props: `{ minorUnits: number }`. Renders a `<Money>` but with a count-up animation on mount, gated by a `motion-safe` check. Settles on the exact value after ~600ms.
- **`<SuccessTick>`** — pure-visual SVG-stroke animation primitive. Used on the Transfer confirm step. `motion-safe` gated; renders as a static check on `reduce`.
- **`<StepFlow>`** — a generalisation of the RegisterLayout pattern: takes a list of steps `[{ id, title, render }]`, exposes a `useStepFlow()` hook to step components for `next` / `back` / current index. Holds form-state across steps (RHF context). Each step change focuses the new step's `<h1>` per ADR-0006 #1 extended to in-page transitions.
- **`groupTransactionsByDate`** — pure function: `(txns: TransactionItemResponse[]) => [{ dateKey: 'today' | 'yesterday' | ISO date, label: string, items: TransactionItemResponse[] }]`. Uses date-fns. Today/Yesterday labels use the User's local time zone. No timezone abstraction beyond what date-fns offers.
- **`resolveCategoryIcon`** — pure mapping: `(category: TransactionCategory | undefined) => LucideIcon`. Default icon when category is undefined. Single switch.
- **`<TransactionDetailSheet>`** — a shadcn `Sheet` (existing component) wrapping a layout block. Bottom-anchored on mobile (`side="bottom"`), right-anchored on desktop (`side="right"`). Drag-to-dismiss on mobile; Escape and backdrop-tap always. Focus trap while open; focus returned to the originating row on close.

### Page-level

- **AccountsPage** — three vertical sections: greeting + `<Balance>` total, quick-action icon row (Send / Payees / Settings, each a `<Link>` with an icon + label), and an Account card stack. The total balance is summed in the page component (sum over `accounts.map(a => a.balanceMinorUnits)`), not in a separate hook — single-use until a second caller exists.
- **AccountDetailPage** — header (name + `<Balance>` + masked sort-code/account-number + Send-money CTA), then `groupTransactionsByDate(transactions)` rendered as sticky-date sections. Each row opens `<TransactionDetailSheet>` on tap; running balance lives only in the sheet, not on the row.
- **TransferPage + AddPayeePage** — refactored on top of `<StepFlow>`. Existing form schemas (Zod from `openapi:gen`) are unchanged; only the step decomposition and the confirm step are new. The existing Move-money sub-nav and the `?from=` query handling from the prior PRD remain intact.
- **All other authenticated pages** (Settings, Payees, AccountNotFound, DirectDebits, StandingOrders) — inherit tokens and typography; layout work brings them onto the same card-and-row treatment used by Transactions. SettingsPage is grouped into Profile / Security / Appearance sections; the Appearance section contains the Theme switcher (re-used surface) and a Mode toggle.
- **All auth pages** (Login, ForgotPassword, ConfirmForgotPassword, ConfirmRegister, SignInHelp, RegisterLayout + three step pages) — same Inter + tokens + radii treatment; centred single-column with the active-theme wordmark at the top; no nav shell.

### Decisions explicitly bounded

- **Theme persistence is `localStorage`-only.** No backend contract change. If a server-side preference is ever wanted, the migration is sync-on-signin from `localStorage`. (Asked in question 5 of the grilling session.)
- **Mode default = system preference on first visit, override persisted on User toggle.** `next-themes` `defaultTheme="system"` + `enableSystem`.
- **PWA install icons are malai-branded only.** Active Theme does not affect the favicon or the home-screen icon. (ADR-0013.)
- **Mobile-only `ProfilePage`; desktop redirects.** Avoids two reachable homes for the same affordances.
- **Page transitions are NOT in scope.** Only the three motion moments — drawer slide, success tick, balance count-up — ship in v1. (Question 17.)

## Testing Decisions

A good test in this repo verifies **external behaviour visible to a User**, not implementation detail. Following existing conventions in `AppShell.test.tsx`, `LoginPage.test.tsx`, and the demo-path Playwright specs:

- Render component, exercise via Testing Library queries that mirror what a User can see / do, assert on what a User would observe.
- For pure functions (`groupTransactionsByDate`, `resolveCategoryIcon`, `Money` formatting), Vitest unit tests assert input → output.
- Avoid asserting on class names, internal state, or render counts.
- No comments anywhere (workspace rule).

### Modules under test

- **`useTheme` + `<ThemeProvider>`** — Vitest, jsdom. Asserts: default theme = `malai`; `setTheme('halcyon')` updates `document.documentElement[data-theme]` and writes to `localStorage`; rehydrating from `localStorage` restores the theme on mount; an unknown stored value falls back to `malai`.
- **`<Money>`** — Vitest. Asserts: `1234567` minor → `£12,345.67`; signed display rules (`-£1.00` for negative, `+£1.00` when `signed: 'always'` and positive); zero handling (`£0.00`); `tabular-nums` applied. Prior art: any existing utility tests; if none, this becomes the pattern.
- **`<Balance>`** — Vitest. Asserts: renders final value (not the animation midpoint) after `await` for the animation duration; `prefers-reduced-motion: reduce` makes the value appear immediately (mock `matchMedia`); accessibility name uses the final value, not the in-flight value.
- **`<SuccessTick>`** — Vitest. Asserts: renders a static check under `prefers-reduced-motion: reduce`; `aria-hidden` set so screen readers do not announce it as decoration.
- **`<StepFlow>`** — Vitest + Testing Library. Asserts: rendering step 1 shows step-1 content; calling `next()` advances; calling `back()` returns; form state is preserved across step changes; focus moves to the new step's `<h1>` on step change.
- **`groupTransactionsByDate`** — Vitest, pure. Asserts: today's txns under "Today"; yesterday's under "Yesterday"; older under formatted date; mixed inputs preserve newest-first within each group; an empty input returns an empty array.
- **`resolveCategoryIcon`** — Vitest, pure. Asserts: known category maps to expected icon; unknown category falls back to the default; `undefined` falls back to the default.
- **`AppShell`** — Vitest + Testing Library. Asserts: at desktop breakpoint (mock viewport) the top nav is visible and the bottom tabs are absent; at mobile breakpoint the bottom tabs are visible (three tabs, Profile included) and the top nav is absent; tapping a tab navigates and updates the active-tab indicator; the Profile tab is absent from desktop nav. Existing `AppShell.test.tsx` is the prior art and is updated rather than duplicated.
- **`ThemeSwitcher` integration** — Vitest. Asserts: opening the switcher lists three themes with the active one marked; selecting a theme updates `document.documentElement[data-theme]` and persists to `localStorage`; the header wordmark re-renders with the matching brand.
- **Page-level (existing tests updated)** — `AccountsPage`, `AccountDetailPage`, `LoginPage`, `ConfirmRegisterPage`, `ConfirmForgotPasswordPage`, `ForgotPasswordPage`, `SettingsPage`, `AccountNotFoundPage`, `SignInHelpPage`, and the page tests under `pages/register/` and `pages/move/` are updated to match new layouts. New tests added for `ProfilePage`. None of these assert on Tailwind class names; all assert on visible roles, labels, and text.
- **Keyboard-only Playwright spec** (`tests/e2e/keyboard.spec.ts`) — walks the demo path using only Tab / Shift+Tab / Enter / Space / Escape. Reuses the shared-Cognito `storageState` from `tests/e2e/auth.setup.ts`. Asserts that after each navigation the document's active element is an expected target (page `<h1>`, primary CTA, etc.).

### Prior art

`AppShell.test.tsx` (existing) — pattern for component tests asserting on visible DOM, not props.
`tests/e2e/a11y.spec.ts` (existing) — pattern for axe-core-driven Playwright tests reusing shared storage state.
`ConfirmCodeField.test.tsx` (existing) — pattern for jsdom-driven user-event flows over a single component.

## Out of Scope

- **Server-side theme persistence.** Theme lives in `localStorage` only. The `User` shape in OpenAPI is unchanged.
- **A holder switcher / multi-AccountHolder UI.** The **Active AccountHolder** resolution remains "the first AccountHolder with type INDIVIDUAL"; a switcher is added when a multi-holder demo scenario arrives.
- **Service worker, offline shell, background sync.** Per ADR-0013.
- **Page-transition animations** (slide-in / slide-out on mobile back-nav). Per question 17 — high code cost, low demo lift.
- **A high-contrast Theme** as a fourth catalogue entry. Per question 18 — conflicts with the locked three-brand framing.
- **Screen-reader manual smoke pre-release.** Per question 18 — explicit ADR-0006 baseline + keyboard E2E is the agreed scope.
- **Swipe-to-reveal on transaction rows, parallax hero, gesture-based navigation** — Monzo-style generous motion. Outside the Starling/Chase target.
- **A statements / cross-account activity feed.** Per question 8 — not on the brief, would require an API surface we don't have.
- **Editing a saved Payee.** Still bounded by [ADR-0009](../../docs/adr/0009-payees-no-edit-no-fake-patch.md); design polish on `PayeesPage` does not change the create / delete contract.
- **Internationalisation.** All copy remains en-GB. Locale is hard-coded to `en-GB` on the `Intl.NumberFormat` calls.
- **Direct Debits / Standing Orders mutations.** Read-only, per the prior Move-money slice's bounds.

## Further Notes

- **The order matters.** Foundation (`useTheme`, theme tokens, typography, PWA) and the shell rework should land first. Primitives (`<Money>`, `<Balance>`, `<StepFlow>`, etc.) land next. Page redesigns wait until the primitives exist — otherwise the first page to redesign invents the primitives ad-hoc and the second one diverges.
- **Token discipline is non-negotiable.** Every Tailwind class on every page should reference design tokens, not literal palette colours. The ESLint rule from ADR-0003 catches the easy misses; manual review catches the rest. A theme can be added only because nothing leaks a literal — that bar is what makes the three-brand demo work.
- **AA contrast is per-Theme, per-Mode.** Before each new Theme block is merged, every token pair gets a manual contrast pass. ADR-0006 #4 flags when this stops scaling — three themes is still inside that envelope.
- **Sarah-Mitchell-only fixtures are stable.** Auto-memory records that registration creates `CURRENT` only and `SAVINGS` is a Sarah-Mitchell-only demo fixture. The hero treatment, quick actions, and card stack respect that — a freshly-registered demo persona shows one Account card; Sarah shows two.
- **Wordmark SVGs.** Three small files. The malai wordmark is the brand surface that the favicon and PWA icon derive from — design it first.
- **CONTEXT.md is updated** with the new vocabulary (Theme, Mode, Profile tab) and the now-triple-overloaded "profile" disambiguation. New code should use the Theme / Mode / Profile-tab terms exactly.
