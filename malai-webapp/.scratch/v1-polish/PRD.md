# v1 polish: client-feedback round, post-design-and-themes

Status: ready-for-agent

## Problem Statement

The first cut of the webapp has shipped and a client demoed it. The shell, theme catalogue, page redesign, PWA install set, and auth surface are all in place (issues 01–11 of the `design-and-themes` PRD are complete). The client's feedback identifies five concrete frictions and invites further polish:

1. **Registration's date-of-birth field rejects valid input** ("Invalid ISO date"), and the native `<input type="date">` is genuinely hard to use for a past date — the year is fiddly to enter and the popup calendar is built around picking _future_ dates, not births.
2. **Balances render with a per-digit count-up animation** on first paint of the AccountsPage total-balance hero and the AccountDetailPage hero card. The client reads this as gamification — wrong tone for a bank.
3. **On mobile, the top chrome is gone**: the wordmark is hidden, so there is no "tap to go home" affordance on any page below the dashboard, and the primary horizontal nav (only present on desktop) is absent. The bottom tab bar exists but does not fully substitute — there is no orientation cue at the top of any mobile page.
4. **The dashboard's "Total balance" hero card** summing every Account into a single figure reads as confusing and unfamiliar. The client correctly observes that UK retail-banking apps (Monzo, Starling, Chase UK, Lloyds, Halifax, Barclays) do not aggregate balances across accounts in their home view.
5. **The Transfer flow forces an explicit Continue click after each single-option selection.** When a step is "pick one of N accounts" or "pick one of N saved payees", the click-to-pick + click-to-continue reads as bureaucratic — the pick was already the commitment.

Plus an open invitation: _"anything you might consider improvements, shout!"_

All five frictions show up in a live demo to a bank decision-maker. The dashboard hero and the count-up are the loudest because they are the first thing on screen; the DOB is the most embarrassing because it stops Registration dead; the mobile top chrome makes the app read as half-finished on the device the brief is built for; the Transfer friction is small but compounds every time the demoer walks through the headline flow.

## Solution

Seven focused changes, deliverable as independent issues:

- **Replace the DOB native date input with a three-field D/M/Y entry** (GOV.UK pattern). Three numeric fields, each validating independently with humane messages. Combine to `YYYY-MM-DD` on submit. Drop the reliance on the native picker entirely, which fixes both the validation error and the year-entry / past-date UX in one stroke.
- **Delete the balance count-up animation.** Replace the `Balance` count-up component with the existing static `Money` primitive at both call sites (AccountDetailPage hero, AccountsPage hero — the second is going away anyway). `Balance.tsx` itself becomes deletable.
- **Add a sticky mobile-only top bar to `AppShell`**: clickable `HeaderWordmark` on the left (→ `/`), a per-page title on the right, nothing else. The bottom tab bar remains the primary nav per [ADR-0010](../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md), which is amended to record the new chrome layer. The user menu and theme switcher stay in the Profile tab on mobile — one place for each thing.
- **Remove the `TotalBalanceHero` from the AccountsPage.** The home view becomes: greeting → quick actions → accounts list. Each Account row keeps its own balance. The master visibility toggle stays available as a small icon button next to the "Accounts" section heading so the privacy-in-public affordance survives. `useTotalBalance` is deleted with the hero. Rationale is captured in [ADR-0014](../../docs/adr/0014-per-account-balances-no-aggregate-hero.md).
- **Auto-advance in `StepFlow` on confirmed radio selection.** Step config gains an opt-in flag; the Source step and the two radio-only Destination sub-modes (My accounts, Saved payee) opt in. New-payee, Amount, and Review keep their explicit CTAs. Confirmed selection means click / Space / Enter — arrow-key navigation does not fire auto-advance, so keyboard users can still arrow-compare before committing. Rule captured in [ADR-0015](../../docs/adr/0015-step-flow-auto-advance-on-confirmed-selection.md).
- **Verb-led CTAs in the Transfer flow.** Replace generic "Continue" with what comes next on each step ("Choose destination", "Enter amount", "Review", "Send money"). Cheap to change, sharpens the bank-app feel.
- **Tone down the dashboard greeting + hide "My accounts" mode when the holder has one account.** Drop the "Good morning/afternoon/evening" time-of-day modifier in favour of a more reserved "Welcome back, {firstName}". On the Transfer flow's Destination step, when the **Active AccountHolder** has only one **Account**, suppress the _My accounts_ segmented-control option entirely instead of rendering an empty-state message.

After this PRD lands: Registration completes without a wall, the dashboard reads as restrained UK retail, the mobile chrome carries the wordmark on every page, and the Transfer flow loses one click per radio-only step.

## User Stories

### Date of birth on Registration

1. As a registering **User**, I want to enter my date of birth as three short numeric fields — Day, Month, Year — so that I can type my DOB at the same speed I would on a paper form, without fighting a date-picker built for choosing future dates.
2. As a registering **User**, I want each field to be a plain numeric input that accepts only digits (with the right `inputmode` so my phone keyboard shows the number pad), so that the entry feel matches GOV.UK and HMRC forms.
3. As a registering **User**, I want the Year field to be wide enough for four digits and the Day / Month fields to be narrow (two digits each), so that the visual shape of the trio cues the expected input.
4. As a registering **User** entering an invalid component, I want a humane error message ("Enter a day between 1 and 31", "Enter a real month", "Year must be a four-digit number", "Enter a date in the past") instead of "Invalid ISO date", so that the message tells me what to do, not what failed.
5. As a registering **User** entering a day that does not exist in the chosen month and year (e.g. 30 February, or 29 February in a non-leap year), I want a single combined error explaining the calendar issue, so that I'm not told "the day is fine" and "the month is fine" but the date is still rejected.
6. As a registering **User** entering a future date, I want the combined date to be rejected with "Enter a date in the past", so that an off-by-typo on the year cannot pass validation.
7. As a registering **User** leaving any of the three fields blank, I want a clear "Enter your date of birth" message, not three separate per-field errors, so that the field reads as one concept.
8. As a registering **User** with a screen reader, I want the three fields wrapped in a `<fieldset>` with a `<legend>` of "Date of birth", and per-field labels announced as the legend + field name, so that the grouping is conveyed.
9. As a registering **User** submitting the form, I want the combined value sent as `YYYY-MM-DD` exactly as the backend's `RegisterRequest.dateOfBirth` expects, so that the contract is unchanged and the backend cannot tell the difference.
10. As a registering **User**, I want my DOB fields preserved if I navigate back from the Address step, so that the back-step behaviour matches the rest of the multi-step Register pattern.

### "Gamification" on amounts — balance count-up removed

11. As a signed-in **User** opening the AccountDetailPage, I want the balance to render at its final value immediately on first paint, so that the figure reads as a fact, not as a slot-machine reveal.
12. As a signed-in **User** with `prefers-reduced-motion: reduce`, I want the same static rendering (no regression), so that the existing motion-safe contract is intact.
13. As a future contributor reading the codebase, I want the `Balance` count-up component to no longer exist, so that nothing tempts a re-introduction. The static `Money` primitive is the single rendering path for currency figures app-wide.

### Mobile top chrome — sticky wordmark + page title

14. As a signed-in **User** on a phone, I want a thin sticky top bar at the top of every authenticated page, carrying the malai wordmark on the left and the current page's title on the right, so that I always have orientation and a tap-to-home affordance.
15. As a signed-in **User** on a phone, I want tapping the wordmark to navigate me to `/` (Accounts), so that "go home from anywhere" costs one tap on any screen.
16. As a signed-in **User** on a phone, I want the wordmark to be the same SVG that desktop renders in the top header, so that the brand presentation is consistent across breakpoints and Theme switches still swap it.
17. As a signed-in **User** on a phone scrolling a long page (e.g. a 100-row transactions list), I want the top bar to remain sticky at the top, so that the home affordance never disappears mid-scroll.
18. As a signed-in **User** on a phone, I want the per-page title text to identify the screen ("Accounts", "Current account", "Send money", "Payees", "Direct Debits", "Standing Orders", "Profile", "Settings"), so that the chrome carries orientation cues the bottom tab bar cannot.
19. As a signed-in **User** on a phone, I want the top bar NOT to carry user-menu, theme-switcher, or horizontal nav links, so that there is one canonical home for Profile-tab actions (per ADR-0010, preserved).
20. As a signed-in **User** on a desktop (≥ `md:` breakpoint), I want the existing desktop header (wordmark + horizontal nav + theme switcher + user menu) to be unchanged by this work, so that the desktop chrome is not broken in service of the mobile fix.
21. As a signed-in **User** on a phone with a notch / Dynamic Island, I want the top bar to respect the iOS / Android safe-area inset at the top, so that the wordmark and title are not clipped or rendered behind the notch.
22. As a signed-in **User** on a page that anchors a CTA to the top (e.g. an account-detail Send-money button), I want the layout to account for the sticky top bar, so that the CTA does not collide.

### Dashboard total-balance hero — removed

23. As a signed-in **User** landing on `/`, I want a single greeting heading ("Welcome back, {firstName}") with no aggregate balance figure above the accounts list, so that the home view reads like Monzo / Starling / Chase, not like a personal-finance aggregator.
24. As a signed-in **User** landing on `/`, I want the row of quick actions (Send money, Manage payees, Settings) directly under the greeting, so that the most common destinations sit higher and are reached faster.
25. As a signed-in **User** landing on `/`, I want each **Account** card below the quick actions to continue showing its own balance, so that the "where is my money" question is answered per Account, not as an aggregate.
26. As a signed-in **User**, I want a small "Hide balances" / "Show balances" icon button placed at the top-right of the "Accounts" section heading, so that the master visibility toggle stays available without a hero card.
27. As a signed-in **User** toggling the master visibility, I want every Account card balance and the AccountDetailPage hero balance to honour the master state simultaneously, so that the privacy affordance is comprehensive (no behavioural regression vs today).
28. As a signed-in **User** on the AccountsPage, I want no `n+1` network fan-out of per-Account balance fetches just to compute a number that is no longer rendered, so that first paint is incrementally faster on a real demo connection.
29. As a future contributor reading the codebase, I want `useTotalBalance` to no longer exist, so that the absence of an aggregate is structural, not a render-time omission.

### StepFlow auto-advance on confirmed selection

30. As a signed-in **User** on the Transfer flow's Source step, I want clicking (or pressing Space / Enter on) a source-Account radio to immediately advance me to the Destination step with no Continue button click, so that the flow feels two-clicks-lighter on the common path.
31. As a signed-in **User** on the Transfer flow's Destination step with **My accounts** selected, I want clicking a destination-Account radio to immediately advance me to the Amount step, so that the same rule applies symmetrically.
32. As a signed-in **User** on the Transfer flow's Destination step with **Saved payee** selected, I want clicking a saved-payee radio to immediately advance me to the Amount step, so that the same rule applies symmetrically.
33. As a signed-in **User** on the Transfer flow's Destination step with **New payee** selected, I want the Continue button to remain — the form has three text fields plus a CoPa check on submit — so that I can correct typos before triggering the network round-trip.
34. As a signed-in **User** on the Transfer flow's Amount step, I want the Continue button to remain, so that I can review my amount and reference before advancing.
35. As a signed-in **User** on the Transfer flow's Review step, I want the explicit Send button to remain (no auto-fire), so that the moment-of-weight that defines "real bank" is preserved.
36. As a keyboard-only **User** arrowing through radios on the Source or radio-only Destination steps, I want focus to move between options _without_ auto-advancing — only an explicit Space / Enter / click should fire — so that I can compare options before committing.
37. As a screen-reader **User**, I want the radios to remain `radiogroup`-shaped with `aria-checked` reflecting the current selection (no a11y regression from today), so that the assistive-tech behaviour is unchanged.
38. As a signed-in **User** mid-flow who taps the back-step affordance after an auto-advance, I want to land on the prior step with my selection preserved, so that "I changed my mind" costs one tap.
39. As a future contributor adding a multi-step flow elsewhere in the app, I want auto-advance to be an opt-in flag on a `StepFlow` step config (default off), so that the existing behaviour of all other step flows is unchanged and the rule is reusable.

### Transfer flow verb-led CTAs

40. As a signed-in **User** on the Transfer flow's Source step (when auto-advance is unavailable, e.g. before a selection has been made), I want the disabled-state CTA labelled "Choose source" (or similar verb-led copy), not "Continue", so that the page tells me what action I am missing.
41. As a signed-in **User** on the Transfer flow's Destination step with **New payee** selected, I want the CTA labelled "Verify payee" (or similar) to reflect what Continue actually does (it triggers the CoPa check), so that the click and the consequence match.
42. As a signed-in **User** on the Transfer flow's Amount step, I want the CTA labelled "Review", so that I know clicking it goes to the Review step and does not fire the transfer.
43. As a signed-in **User** on the Transfer flow's Review step, I want the CTA labelled "Send {amount}" (or "Send money" if amount-substitution complicates the test snapshots), so that the commit moment is unambiguous.

### Hide "My accounts" mode when single-Account holder

44. As a signed-in **User** with exactly one **Account** on my Active AccountHolder, opening the Transfer flow, I want the Destination step to not show the **My accounts** segmented-control option at all, so that I am not invited to pick a mode that has nothing to pick from.
45. As a signed-in **User** with exactly one **Account**, I want the initial Destination mode to default to **Saved payee** (or **New payee** if I have no saved payees), so that the first body rendered under the segmented control has actionable content.
46. As a signed-in **User** with two or more **Accounts**, I want the existing three-mode segmented control to continue rendering, so that the multi-Account case is unchanged.

### Greeting tone-down

47. As a signed-in **User** landing on `/`, I want the heading to read "Welcome back, {firstName}" (or "Welcome, {firstName}" if no firstName is present, falling back to the existing `firstNameOf` helper), so that the greeting is reserved enough for a bank.
48. As a signed-in **User**, I want the time-of-day modifier ("Good morning / afternoon / evening") removed, so that the copy does not require a client-side clock and reads less casually.

### Documentation

49. As a future contributor reading `docs/adr/`, I want ADR-0010 amended with a "Mobile top chrome" section, so that the bottom-tab decision and the new sticky-top-bar decision are recorded as one consistent piece.
50. As a future contributor wondering why the dashboard has no aggregate balance, I want ADR-0014 to explain it explicitly (UK retail-banking convention; rejected because PFM-app feel), so that the absence is not re-litigated.
51. As a future contributor adding a multi-step flow, I want ADR-0015 to record the auto-advance-on-confirmed-selection rule and its bounds, so that the rule is reusable without re-deriving it.

## Implementation Decisions

### New deep modules

- **`DateOfBirthInput`** — a new presentational component used in `RegisterAboutPage` (and reusable anywhere else a DOB is captured). Interface: `value: string` (either `""` or `YYYY-MM-DD`), `onChange(next: string)` (called with the canonical `YYYY-MM-DD` once all three sub-fields parse, otherwise with `""`), `error?: string`, `id?: string`, `aria-describedby?: string`. Internally it owns three local `useState`s for day / month / year strings, validates each, derives the combined value, and surfaces a single component-level error message via `error?`. Day-month-year validity respects leap years (delegate to `date-fns`'s `isValid(new Date(...))` or a hand-rolled `isValidDate` to avoid `Date` constructor parsing quirks).
- **`usePageTitle(title: string)` + `useActivePageTitle()` consumer hook** — small Zustand store (or context) where each authenticated page declares its title in an effect on mount. The `AppShell` mobile top bar reads via `useActivePageTitle()`. Unset / unmounted pages fall back to an empty title (the wordmark still renders). Title is a string only — no JSX, no icons, no actions; a v1 constraint that keeps the surface boring.
- **`useAutoAdvance(active: boolean)` + `StepFlow` config opt-in** — a step's config gains `autoAdvanceOnSelection?: boolean` (default `false`). When `true`, the step body wraps its `radiogroup` in an element that listens for _confirmed_ selection — i.e. a `change` event triggered by an explicit user input on a radio (`onClick` / `onKeyDown` for Space / Enter), **not** focus changes or arrow-key roving. On confirmed selection it calls `next()` from `useStepFlow()`. Implementation detail: prefer wiring the auto-advance trigger inside the radio's `onChange` handler with a guard against keyboard-arrow-driven change events (React doesn't natively distinguish, but checking `event.nativeEvent.detail` or attaching a small `pointer-down` / `keydown:Enter|Space` sentinel resolves it).

### Modifications

- **`AppShell`** gains a `<MobileTopBar>` child rendered with `md:hidden` and `sticky top-0 z-40` plus `pt-[env(safe-area-inset-top)]`. Contents: `<Link to="/">` wrapping `HeaderWordmark`, a thin divider, the active page title from `useActivePageTitle()`. The existing desktop `app-header` and `primary-nav-desktop` are unchanged. Main-content padding-top accommodates the new bar.
- **`AccountsPage`** drops `<TotalBalanceHero>` and its inputs (`useBalanceVisibility`'s `toggle` moves down into the new section-heading icon button; `useTotalBalance` and the `Balance` import disappear). The greeting heading changes copy ("Welcome back, {firstName}") and stops calling `greetingFor()` (which becomes dead — delete the helper if unused after the change). The section heading "Accounts" grows a small trailing slot for the master visibility toggle.
- **`AccountHeroCard`** replaces `<Balance minorUnits=... />` with `<Money minorUnits=... />` for the unmasked case. The masked-case `£·····` text and the per-card local-hidden toggle behaviour are unchanged. The `Balance` import is removed.
- **`TransferPage`** does six things:
  - Source step: declares `autoAdvanceOnSelection: true` in its step config; replaces the Continue button label with verb-led copy ("Choose source") and disables it pre-selection; the button is hidden once a selection has been made (auto-advance fires).
  - Destination step: declares `autoAdvanceOnSelection: true` conditionally — only when the active sub-mode is _My accounts_ or _Saved payee_. For _New payee_, the existing Continue button stays and gains the "Verify payee" label.
  - Destination step: if `accountList.length === 1`, omit the _My accounts_ segmented-control option and pre-select _Saved payee_ (or _New payee_ if no saved payees).
  - Amount step: CTA label changes to "Review".
  - Confirm step: CTA label changes to "Send {amount}" or "Send money" (TBD at implementation time; prefer "Send money" to keep snapshot tests stable).
  - All four step bodies declare their page title via `usePageTitle(...)`.
- **Deletions**: `src/accounts/Balance.tsx`, `src/accounts/Balance.test.tsx`, `src/accounts/useTotalBalance.ts`, the `greetingFor` helper if it is no longer referenced.

### Validation rule for the DOB three-field input

A combined date is valid iff:

- All three sub-fields parse to a positive integer
- `day` ∈ 1..31
- `month` ∈ 1..12
- `year` is four digits (1000..current calendar year — backend will reject ages outside its own limits and we do not duplicate that here)
- The combined `(year, month, day)` is a real calendar date (handles leap years and short months)
- The combined date is strictly in the past (no today, no future)

Error message ordering (one message at a time, in priority order):

1. Any field empty → "Enter your date of birth"
2. Any sub-field non-numeric or out of range → "Enter a real date of birth"
3. Combined date is not a real calendar date → "Enter a real date of birth"
4. Combined date is today or in the future → "Date of birth must be in the past"

The single-message approach keeps the surface tight and matches GOV.UK's pattern. The combined `YYYY-MM-DD` value goes to React Hook Form via `onChange`; the existing `zRegisterRequest` validation continues to run and is a redundant safety net.

### Auto-advance, precisely

A step opting into `autoAdvanceOnSelection` fires `next()` when:

1. The user produces a _confirmed_ input on a radio inside the step's primary `radiogroup` — i.e. a click, a `Space` keypress on the focused radio, or an `Enter` keypress on the focused radio.
2. The new selection satisfies the existing "would have allowed Continue" condition.

Arrow-key navigation between radios moves focus and the visually-checked state per the native radio behaviour but **does not** fire `next()`. This is the ADR-0015 rule, encoded by attaching a `pointerdown` / `keydown` sentinel that flips a transient flag before the `change` event fires.

### CONTEXT.md, ADRs

- `CONTEXT.md` is unchanged — no new terms, no shifted meanings.
- ADR-0010 is amended (in this PRD's work) with a "Mobile top chrome" section.
- ADR-0014 (new) records: per-account balances, no aggregate hero.
- ADR-0015 (new) records: step-flow auto-advance on confirmed radio selection.

(Both new ADRs and the ADR-0010 amendment have been pre-landed during the grilling that produced this PRD; the issue work picks up from there.)

## Testing Decisions

### What makes a good test here

External behaviour, not implementation. For the three deep modules:

- **`DateOfBirthInput`** — drive it through `@testing-library/user-event`: type into the fields, assert the `onChange` calls receive `""` mid-typing and `YYYY-MM-DD` once valid; assert the rendered error message matches the priority-ordered rule above; assert keyboard navigation between the three sub-fields. Do **not** test internal state shape.
- **`usePageTitle` + `AppShell` mobile top bar** — render an `<AppShell>` wrapping a child component that calls `usePageTitle("My title")` in an effect; assert the mobile top bar (queried by `data-testid="app-header-mobile"`) shows "My title" and contains a link to `/` with the wordmark. Switch routes, assert title updates. Do not assert against the underlying store implementation.
- **`useAutoAdvance` + `StepFlow` opt-in** — unit-test the hook in a minimal `<StepFlow>` harness with a two-step config: step 1 has a `radiogroup` with `autoAdvanceOnSelection: true`, step 2 is a sentinel. Click a radio → asserts step 2 is now rendered. Arrow-through (focus + arrow keys) → asserts step 1 is still rendered. Space / Enter on a focused radio → asserts step 2 renders. Mouse-click on a label that wraps a radio → asserts step 2 renders.

### Tests to write or update

- **Vitest unit (new)**: `DateOfBirthInput.test.tsx` covering the eight or so case rows (empty / partial / non-numeric / out-of-range / non-real-date / leap-year / future-date / valid).
- **Vitest unit (new)**: `usePageTitle.test.tsx` covering the store mechanics and the AppShell consumer.
- **Vitest unit (new)**: `useAutoAdvance.test.tsx` or `StepFlow.test.tsx` (extended) covering the four trigger cases above.
- **Vitest unit (update)**: `RegisterAboutPage.test.tsx` swaps from the native date input to the new `DateOfBirthInput`. Assert validation messaging and the submitted DOB is `YYYY-MM-DD`.
- **Vitest unit (update)**: `AccountsPage.test.tsx` removes coverage of the total-balance hero, asserts the master visibility toggle now lives next to the Accounts section heading and still toggles per-card balances.
- **Vitest unit (update)**: `AccountHeroCard.test.tsx` replaces `Balance` assertions with `Money`.
- **Vitest unit (update)**: `TransferPage.test.tsx` covers verb-led CTA labels, the auto-advance behaviour on Source and on the two radio-only Destination sub-modes, the New-payee mode keeping its explicit Verify-payee CTA, and the one-Account holder case where the _My accounts_ mode is suppressed.
- **Playwright update**: `tests/e2e/keyboard.spec.ts` extends the Transfer walkthrough with an arrow-through-then-Enter case to prove auto-advance does not fire on arrow navigation.
- **Vitest deletions**: `Balance.test.tsx` and the `TotalBalanceHero` test cases go with their code.

### Prior art in the codebase

- `src/move-money/AmountInput.test.tsx` for the three-field tiny-input testing pattern (numeric inputs with parse / format).
- `src/components/StepFlow.test.tsx` for the existing step-flow harness — extend it rather than introducing a parallel test layer.
- `src/pages/AccountsPage.test.tsx` already covers the master visibility toggle behaviour — refactor in place.
- `tests/e2e/keyboard.spec.ts` (Issue 11) for the arrow-through verification idiom.

## Out of Scope

- **Changing the Registration backend contract.** `POST /api/v1/auth/register` still receives `dateOfBirth` as `YYYY-MM-DD`. The new input is purely client-side.
- **A holder switcher.** The single-Account case in the Transfer flow is treated by hiding the _My accounts_ mode; multi-holder switching is still out of v1 scope (per the existing CONTEXT.md note on Active AccountHolder).
- **Per-page TitleBar actions / icons / breadcrumb.** The mobile top bar is wordmark + plain string title only. Per-page CTAs (e.g. an "Edit" button) are not yet a thing.
- **Auto-advance on other multi-step flows** (Register, AddPayee, Forgot Password). ADR-0015 makes the rule reusable, but the rollout in this PRD is the Transfer flow only.
- **Other "gamification" candidates** — the success tick on Transfer success, the skeleton pulse on loading states, the copy-to-clipboard checkmark flash. All explicitly out of scope (confirmed during grilling).
- **A "from now on" deletion of the `Balance` component**: deleting the file is in scope; _banning_ the count-up pattern via lint is not (no precedent in the repo for that).
- **Backend-side DOB minimum-age validation.** The schema today does not enforce one. We do not add a synthetic one client-side; reality (e.g. UK 18+) lives with the backend if it lives anywhere.
- **Dashboard greeting personalisation beyond "Welcome back, {firstName}"** — no daily quotes, no "you have N new transactions" copy. Reserved tone is the v1 ceiling.

## Further Notes

- The five frictions in the brief came from a single demo session. If a second session surfaces a different read on any of these decisions (especially the no-aggregate-hero call), the relevant ADR has a "When this ADR is superseded" section to walk forward from.
- The `Balance` count-up was introduced as part of the original `design-and-themes` Solution (user story 27 of that PRD), which means this PRD partially supersedes that one for the affected stories. The `design-and-themes` PRD is not edited — its issues are all marked done, and the supersession is recorded here.
- The Transfer flow's _New payee_ mode keeps an explicit Continue because the CoPa check runs on Continue, not on radio selection. Wiring auto-advance there would require firing CoPa on every keystroke in the three text fields, which the back end's idempotency budget and the client's rate limits would not love.
- Auto-advance was rejected on the Destination _mode segmented control_ (My accounts / Saved payee / New payee) specifically because picking a mode does not commit a destination — it changes which body renders below. Auto-advancing on mode would surprise the user. This is captured in ADR-0015's "considered options" section.
- The mobile top bar carries the wordmark, not a backwards-pointing chevron. Per-page back navigation is delegated to the browser's back gesture (PWA-installed sessions get the platform back button). A dedicated `<` affordance can be added per-page later if a demo flow surfaces a need.
