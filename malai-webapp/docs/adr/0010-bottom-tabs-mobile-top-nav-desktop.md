# Bottom-tab nav on mobile, top nav on desktop

The brief is mobile-first and the visual idiom is Starling / Chase UK. Both bookend the same convention: a bottom tab bar on phones (thumb-reachable, three tabs — **Accounts**, **Move money**, **Profile**) and a top nav on tablet/desktop (Accounts, Move money, with the user menu — Settings, Sign out, theme switcher — in a header dropdown). The AppShell renders a single nav config twice at the `md:` breakpoint; pages that anchor a fixed CTA (Send money, Add payee confirm step) honour bottom-safe-area padding so the CTA doesn't collide with the tab bar.

The header user-menu dropdown remains the **desktop-only** home for Settings / Sign out / theme — on mobile, those affordances live behind the **Profile tab** (CONTEXT.md disambiguates the third overload of "profile"). The header dropdown is suppressed on mobile so we don't ship two reachable copies of the same actions.

## Mobile top chrome — sticky wordmark + page title

The original cut of this ADR rendered _no_ top chrome on mobile: the bottom tab bar was the only navigational surface, and the wordmark only existed inside the desktop `app-header`. Demo feedback flagged the gap — without a wordmark anywhere on a mobile page, the user has no "tap to go home" affordance, and no orientation cue for which screen they're on. The bottom tab bar handles between-section navigation but says nothing about _where the user already is_.

The amendment: a **mobile-only sticky top bar** rendered by `AppShell` carrying two things and nothing else — the clickable `HeaderWordmark` (links to `/`) on the left, and a per-page title on the right. No user menu, no theme switcher, no horizontal nav links. Those stay in the **Profile tab** so the one-place-for-each-thing rule from the second paragraph still holds: the mobile top bar is chrome and home-affordance, not a duplicate nav.

Per-page titles are supplied by each authenticated page via a small `usePageTitle("…")` hook (or `<AppShell.PageTitle>` slot — picked at implementation time). The bar is sticky so the wordmark is always one tap away even when the user has scrolled mid-list.

## Considered options

- **Bottom tabs (mobile) + top nav (desktop) (accepted).** Matches the chosen visual idiom and the brief's mobile-first ask. Costs an AppShell refactor and bottom-safe-area discipline on the four CTA-anchored pages.
- **Keep the current top nav + hamburger on mobile.** Zero refactor cost. Reads as a generic web app on a phone, not as a banking app — undermines the demo pitch.
- **Bottom tabs everywhere (mobile and desktop).** Single consistent pattern, but unusual on desktop and wastes horizontal real-estate.

## Consequences

- Every authenticated page must reserve bottom padding for the tab bar on mobile (`pb-[calc(env(safe-area-inset-bottom)+56px)]` or equivalent). Pages that fix a CTA to the bottom need to account for both the tab bar and the safe-area inset.
- The `Profile tab` is mobile-only — desktop has no equivalent destination because the header dropdown covers the same surface. CONTEXT.md flags this asymmetry.
- Toasts (sonner) must anchor above the tab bar on mobile to remain visible.
