# PRD: Balance forecast — the webapp card and screen

Status: ready-for-agent

## Problem Statement

A malai customer looking at one of their **Accounts** sees a single number — the balance right now — and nothing about where it is heading. Their month is already largely committed: a **Standing Order** pushes rent out on the 10th, an energy **Direct Debit** collects on the 15th, the gym standing order leaves on the 28th. A real UK retail bank (HSBC's "Balance forecast" / "Balance after bills" is the reference the client supplied) tells the customer _where their balance lands by month-end_, splitting the recurring payments into the ones already gone and the ones still to come, and flagging when the account is heading into the red.

malai already holds the raw material and, as of the backend slice, computes the projection: the new `GET /api/v1/accounts/{accountUid}/forecast` returns a **Balance forecast** for the current calendar month. But the webapp does not surface it. A bank decision-maker exploring the demo — whose headline question is _"this looks like a real bank, and the architecture beneath it is one we'd actually want to run"_ — hits a gap exactly where a modern retail app is most helpful: the account screen shows a ledger's "now" but not a bank's "where this is going."

## Solution

Surface the **Balance forecast** in two places on the account, off the frozen OpenAPI contract, using the same insights-read-model shape the **Spending** surface established.

- **A dashboard card** on the account detail screen, sitting directly below the balance hero and above the action CTAs (matching the reference). It shows the headline **forecast balance** for the month-end ("Balance forecast — for 31 July") and a **warning** when that number is negative, linking through to a dedicated screen. The card is always present when the endpoint returns: for an account with nothing due it shows the forecast (equal to the current balance) with a "No payments due this month" note and no warning; when the endpoint fails closed (`503`) it shows a compact "unavailable" state rather than vanishing.

- **A dedicated Balance forecast screen** at `/accounts/:accountUid/forecast`. A **forecast hero** leads with the big signed month-end number, warning-styled when negative, followed by a compact **breakdown** (available balance and the expected-payments total that move it), then the **forecast timeline**: two collapsible sections, **Expected** (open by default — the payments still to leave) and **Completed** (collapsed — the payments already gone, shown for context). Each timeline row shows the date, counterparty name, signed amount, and a **Standing Order** / **Direct Debit** tag, and links back to the underlying instrument.

The forecast is **outgoings-only** (income never appears) and always about the **current calendar month** — there is no month navigation, unlike Spending, because a forecast is inherently about "now". The projection can go negative even though a `NO_OVERDRAFT` **Account**'s real balance cannot; that negative _is_ the month-end warning. malai models **no arranged overdraft**, so — deliberately diverging from the HSBC reference — the UI shows no overdraft line, limit, or headroom framing, and styles the warning with the `destructive` (red) token rather than the reference's amber caution: in malai a negative forecast means genuinely running out of money, not merely dipping into agreed headroom.

The buyer sees a forward-looking analytics view presented honestly: a bank that tells you where your money is going, and refuses to invent an overdraft it doesn't offer.

## User Stories

### The dashboard card

1. As a malai customer, I want a **Balance forecast** card on my account detail screen, so that I see where my balance is heading without navigating away.
2. As a malai customer, I want the card to name the date it forecasts to ("for 31 July"), so that the projection reads like a real bank's month-end forecast.
3. As a malai customer, I want the card's headline to be the projected month-end balance, so that I get the punchline at a glance.
4. As a malai customer, I want a clear warning treatment on the card when the forecast is negative, so that I'm alerted my account is heading into the red.
5. As a malai customer whose forecast is positive, I want the card to show the number plainly without a warning, so that the alert stays meaningful.
6. As a malai customer with no recurring payments this month, I want the card to still appear, showing the forecast (equal to my current balance) with a "no payments due" note and no warning, so that the feature doesn't look missing.
7. As a malai customer, I want a "View forecast" affordance on the card that opens the full forecast screen, so that I can see the detail behind the number.
8. As a malai customer, I want the card to show a compact "unavailable" state when the forecast can't be produced, so that a backend hiccup doesn't blank out or silently drop the feature.
9. As a malai customer, I want the card to show a loading placeholder while the forecast is fetched, so that the screen doesn't jump.
10. As a malai customer, I want the card placed right below my balance and above the action buttons, so that the "now" and the "where it's going" read together.

### The forecast screen — hero and breakdown

11. As a malai customer, I want a dedicated Balance forecast screen, so that I can study the projection and the payments behind it.
12. As a malai customer, I want the screen to lead with the projected month-end balance in a prominent hero, so that the headline number is unmissable.
13. As a malai customer, I want the hero warning-styled when the forecast is negative, so that the "you won't be fine by month-end" message lands.
14. As a malai customer, I want a breakdown showing my available balance and the total still expected to leave, so that I understand what the forecast is built from.
15. As a malai customer, I want the breakdown figures to visibly reconcile to the forecast (available balance plus expected payments), so that the number is trustworthy under scrutiny.
16. As a malai customer, I want the screen headed as a **Balance forecast** for the month window (e.g. "1 July to 31 July"), so that I know the period it covers.
17. As a bank decision-maker, I want the forecast to start from the account's **available balance** (what's actually spendable now), so that the projection is grounded in spendable money, not a figure inflated by holds.

### The forecast timeline

18. As a malai customer, I want the recurring payments still to come this month listed under **Expected**, so that I can see what is still due.
19. As a malai customer, I want the recurring payments already gone this month listed under **Completed**, so that I can see what has happened for context.
20. As a malai customer, I want the **Expected** section open by default and the **Completed** section collapsed, so that my attention lands on the money still to leave.
21. As a malai customer, I want each section headed with a count ("Expected (11)", "Completed (6)"), so that I know how many payments each holds before expanding.
22. As a malai customer, I want to collapse and expand each section with the keyboard and a screen reader, so that the timeline is usable without a mouse.
23. As a malai customer, I want each timeline row to show the date, the counterparty name, and the signed amount, so that I recognise each payment.
24. As a malai customer, I want each row tagged **Standing Order** or **Direct Debit**, so that I understand what kind of recurring payment it is.
25. As a malai customer, I want the expected payments in date order, so that I read them as a timeline of what's coming next.
26. As a malai customer, I want to tap a row to jump to the underlying standing order or direct debit, so that I can inspect or manage the instrument behind it.
27. As a malai customer with an instrument that pays more than once this month, I want each of its occurrences listed as its own row, so that the timeline reflects the true number of payments.
28. As a malai customer with nothing expected this month, I want the Expected section to say so cleanly rather than sit empty, so that the screen still reads as finished.
29. As a malai customer, I want only my recurring payments in the timeline — not every transaction — so that it stays about my committed payments, not discretionary spending.
30. As a malai customer, I want incoming money like my salary excluded, so that the forecast is about outgoings, matching how a bank presents it.

### Correctness, scope and resilience

31. As a malai customer, I want the forecast scoped to the one account I'm viewing, so that it lines up with that account's balance and transactions.
32. As a malai customer, I want a cancelled standing order or direct debit left out of the forecast, so that payments I've stopped don't inflate it. (Enforced by the backend; the webapp renders what it returns.)
33. As a malai customer, I want the forecast screen to tell me clearly when it can't be produced (a dependency is down) rather than showing a half-built projection, so that I never act on a misleading forecast.
34. As a malai customer, I want a loading state on the forecast screen while it fetches, so that the layout doesn't flash.
35. As a malai customer, I want the forecast to only cover accounts I own, so that my projections stay private. (Enforced by the backend's ownership check; the webapp passes the **Active AccountHolder** header.)
36. As a bank decision-maker, I want the completed payments reflected in my available balance, not double-counted, so that the numbers are internally consistent. (Guaranteed by the contract; the webapp never re-subtracts Completed rows.)

### Demo experience

37. As a newly-registered demo user, I want my forecast populated the moment I open my account, so that the feature never looks empty.
38. As a newly-registered demo user, I want some payments already completed and some still expected, so that both timeline sections are populated like the reference.
39. As a newly-registered demo user, I want my seeded forecast to dip into the red, so that the warning state is visible when I explore on my own. (Seeding is a backend concern; the webapp must render the negative case correctly.)

## Implementation Decisions

**Prerequisite — refresh the contract.** `GET /api/v1/accounts/{accountUid}/forecast` and `BalanceForecastResponse` / `ForecastOccurrence` / `ForecastInstrumentType` exist in the backend source-of-truth `openapi.yaml` but not yet in this repo's snapshot or generated client. Refresh `./openapi.yaml` from the backend, then `npm run openapi:gen`, before any component work. All types below are the generated ones.

**Surface shape — reuse the Spending precedent.** The **Balance forecast** is another insights read-model, mounted per-**Account**, and follows the Spending pattern: a TanStack Query hook feeding presentational components, with a `503` mapped to an "unavailable" state (fail-closed, per the contract). Modules live in `src/accounts/` alongside the Spending ones; the screen page lives in `src/pages/`. Respects ADR-0002 (no router loaders — data fetched in components) and ADR-0014 (per-account, not an aggregate hero).

**Entry point — a card, not a button.** Unlike Spending (reached via a CTA button), the forecast is surfaced as a rich **dashboard card** so the headline month-end number and warning are visible on the account screen itself — the demo's punchline. `AccountDetailPage` mounts the card below the hero and above the CTA row. Spending's button is unchanged.

**`useForecast(accountUid)`** — the data module, twin of `useSpendingBreakdown` **minus the month param** (the forecast is always the current month). It wraps the generated forecast `queryOptions`, injects the `MAL-Account-Holder-Id` header from the **Active AccountHolder**, gates the query on holder readiness, and returns `{ forecast, isPending, isError, isUnavailable, error }` where `isUnavailable` is `true` when the error is HTTP `503`. No writes, no idempotency key.

**`forecastOccurrenceHref(occurrence)`** — a pure helper mapping a `ForecastOccurrence` to its deep link: `STANDING_ORDER → /move/standing-orders#<instrumentId>`, `DIRECT_DEBIT → /move/direct-debits#<instrumentId>`. Isolating this keeps the row-linking decision in one tested place.

**Deep-linking rows.** Timeline rows link to the instrument's read-only list page with a fragment to the instrument's id, reusing the existing `PayeeRow` anchor mechanism (native browser fragment-scroll — no JS, no highlight). `StandingOrderRow` and `DirectDebitRow` gain `id={standingOrderId}` / `id={directDebitId}` so the fragment has a target. Multiple occurrences of one instrument all link to the same anchor, which is correct.

**Card states.** Loading → skeleton; `503` → compact "Balance forecast — unavailable just now"; empty (no occurrences either side) → forecast number (equal to available balance) with a "No payments due this month" note and no warning; populated → "Balance forecast / for {windowEnd} / {forecastBalance}" with the warning treatment when `forecastBalanceMinorUnits < 0`. The card is always rendered in one of these states when the query resolves — it never conditionally disappears.

**Screen layout.** Forecast hero (big signed `forecastBalanceMinorUnits`, warning-styled when negative) → breakdown (`availableBalanceMinorUnits`, `expectedTotalMinorUnits`) → forecast timeline. The timeline is two collapsible sections built on native `<details>/<summary>` (free disclosure + keyboard a11y, no new dependency, keeps the `src/components/ui` primitive set minimal): **Expected** (`open` by default) then **Completed** (collapsed), each with a count in its summary. Rows render `date`, `counterpartyName`, a signed `amountMinorUnits` via the existing `Money` component, and the instrument-type tag, and are links via `forecastOccurrenceHref`. The screen has **no month navigation**. It carries a `now?` prop (like `SpendingPage`) purely so component tests can pin date formatting deterministically; production uses the real clock only for display formatting, since the window comes from the response.

**Warning treatment — red, not amber; no overdraft.** The warning is derived purely from the sign of `forecastBalanceMinorUnits`; there is no near-zero caution state and no overdraft line, limit, or headroom framing. It uses the theme-aware `destructive` token plus a `TriangleAlert` icon, diverging deliberately from the HSBC reference's amber: with no arranged overdraft, a negative forecast is genuine overspend, not agreed headroom. This honours ADR-0003 token discipline (no new ad-hoc colour). Captured as glossary language in `CONTEXT.md`.

**Money and dates.** Signed amounts render through the existing `Money` component (default `signed="negative"` already shows a leading minus for negatives and plain for zero/positive). The month-end headline formats `windowEnd` with `date-fns` ("for 31 July"); rows format `date` compactly ("10 Jul"); the timeline sub-heading formats the `windowStart`–`windowEnd` range.

**Routing.** New authenticated route `/accounts/:accountUid/forecast` in `App.tsx`, peer of the existing `/accounts/:accountUid/spending` route, rendering `ForecastPage`.

**Vocabulary.** Card and screen are titled **Balance forecast**; sections are **Completed** / **Expected**; row tags read **Standing order** / **Direct Debit**. The domain terms **Balance forecast** and **Completed / Expected (forecast payments)** are recorded in `CONTEXT.md`. Avoid "balance after bills", "budget", "arranged overdraft", and "occurrence" in user-facing copy.

## Testing Decisions

Good tests here assert **external behaviour** — what the customer sees for a given forecast response — not internal wiring. Deterministic, golden assertions live at the unit/integration layer (Vitest + Testing Library + jsdom), where the forecast response and the clock are injected. End-to-end tests assert only clock-and-data-robust invariants against the real backend, because the deployed clock can't be pinned (the endpoint has no `month`/`asOfDate` param) and the shared Cognito user's seeded instruments aren't deterministic. Every module gets a co-located test, per repo convention.

- **`useForecast` (unit).** Assert the holder-gating (query disabled until the **Active AccountHolder** is ready), the `503 → isUnavailable` mapping, the error and success shapes. Prior art: `useSpendingBreakdown.test.tsx`.
- **`forecastOccurrenceHref` (unit, pure).** `STANDING_ORDER`/`DIRECT_DEBIT` → the correct `/move/...#<id>` path. Prior art: the small pure helpers in `move-money` (`formatFrequency.test.ts`, `findStandingOrdersByPayee.test.ts`).
- **`ForecastCard` (integration).** Inject each hook state and assert: loading skeleton; unavailable card on `503`; empty card (number equal to available balance + "no payments due", no warning); positive populated card (number + date, no warning); negative populated card (warning treatment present). Prior art: `AccountHeroCard.test.tsx`, the Spending states in `SpendingPage.test.tsx`.
- **`ForecastPage` (integration).** With an injected response and a fixed `now`, assert the hero number and its warning styling on the negative case; the breakdown figures; the Completed/Expected split with counts and default open/closed states; date-ascending order; per-row date, counterparty, signed amount, type tag; row deep-link hrefs; the multiple-occurrences case (repeated rows for one instrument); the empty-section copy; and the unavailable/loading states. Prior art: `SpendingPage.test.tsx` (month/now handling, unavailable + empty branches).
- **`ForecastOccurrenceRow` (unit).** Row renders each part and links via `forecastOccurrenceHref`. Prior art: `StandingOrderRow.test.tsx`, `TransactionRow.test.tsx`.
- **`forecast.spec.ts` (e2e smoke, defensive).** Navigate to the forecast screen; assert the hero and breakdown render and the timeline shows either populated sections or the clean empty state — mirroring `move-money-dd-so.spec.ts`'s populated-or-empty-or-error branching. Proves real-backend wiring (auth, holder header, generated-client deserialization) without asserting non-deterministic numbers.
- **`a11y.spec.ts` (extend).** Add the `/accounts/:accountUid/forecast` route to the axe-core sweep, covering the `<details>` disclosure semantics. Prior art: the existing routes in `a11y.spec.ts`; ADR-0006 baseline.

## Out of Scope

- **Backend and contract.** The endpoint, projection engine, internal fan-out, and seeding are done in `malai-backend` (see that repo's `.scratch/balance-forecast`). This PRD consumes the frozen contract; it does not change `openapi.yaml`'s source of truth (only refreshes this repo's snapshot).
- **Arranged overdraft.** No overdraft line, limit, or headroom framing anywhere in the UI. The warning is `forecastBalance < 0`.
- **Editable / custom forecast cycle.** HSBC's "Edit cycle" (payday-anchored or otherwise custom windows) is not built — the window is always the current calendar month, and there is no month navigation.
- **Reconciling Completed rows to real postings.** Completed rows are schedule-projected at nominal amounts, as the contract returns them; the webapp does not correlate them to specific **Transactions**.
- **A per-instrument detail screen.** Deep links land on the existing read-only **Standing Orders** / **Direct Debits** list pages (anchored to the instrument), not a new detail route.
- **Multi-currency.** Single account currency (GBP in v1), as elsewhere.
- **An aggregate/all-accounts forecast.** Per-**Account** only, per ADR-0014.
- **A webapp ADR.** The one surprising choice (no overdraft framing; red not amber) is a reversible UI stance already recorded in `CONTEXT.md`; the hard-to-reverse decision lives in the backend's `insights-service` ADR-0002. No new webapp ADR.

## Further Notes

- Build order (dependencies resolved): ① refresh snapshot + regen client → ② `useForecast` + `forecastOccurrenceHref` (+ tests) → ③ `ForecastCard` + mount in `AccountDetailPage` → ④ `/accounts/:accountUid/forecast` route + `ForecastPage` (hero, breakdown, timeline, row) → ⑤ `id={instrumentId}` anchors on `StandingOrderRow` / `DirectDebitRow` → ⑥ `forecast.spec.ts` smoke + `a11y.spec.ts` route.
- The `Money` component's default `signed="negative"` mode already matches the forecast's signed-negative amounts — no new formatting mode needed.
- The reference screenshots (`balance-forecast-*.PNG` at the repo root) are the visual target for layout and copy, with the two documented divergences: no overdraft line, and a red (not amber) warning.
- `SpendingPage`'s `now?` prop and its unavailable/empty/error branches are the closest structural template for `ForecastPage`.
