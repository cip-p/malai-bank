# 03 — Forecast timeline + instrument deep-links

Status: done

## Done (2026-07-09)

Shipped the Expected/Completed timeline on the forecast screen and the
instrument deep-links. `forecastOccurrenceHref` + `ForecastOccurrenceRow` live in
`src/accounts/` beside the other forecast modules; `ForecastPage` gained a
`ForecastSection` disclosure (native `<details>/<summary>`, Expected open,
Completed collapsed, count in each summary, empty-copy fallback). `StandingOrderRow`
and `DirectDebitRow` now carry `id={instrumentId}` so the fragment scrolls to the
instrument, reusing the `PayeeRow` anchor mechanism.

Verification: `npm run test` 878 green (2 helper + 4 row + 8 new page cases + 2
SO/DD anchor cases), `npm run typecheck`/`lint`/`prettier` clean. `forecast.spec.ts`
e2e smoke and the `a11y` sweep of the live timeline were **not run here** (no local
malai-api/Postgres/Cognito stack on this box) — run them once the stack is up. The
a11y route entry for `/accounts/:accountUid/forecast` already exists from slice 02,
so the timeline is covered by the existing sweep without an a11y.spec.ts change.

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

The **forecast timeline** on the Balance forecast screen, plus the deep links from each payment back to the instrument behind it.

Below the hero and breakdown, render two collapsible sections built on native `<details>/<summary>` (free disclosure + keyboard a11y, no new dependency): **Expected** (open by default — the payments still to leave this month) and **Completed** (collapsed — the payments already gone, shown for context). Each summary carries a count, e.g. "Expected (11)" / "Completed (6)". When a section is empty it shows a short "nothing expected / completed this month" line instead of sitting blank.

Each `ForecastOccurrenceRow` shows the payment `date` (compact, e.g. "10 Jul"), the `counterpartyName`, the signed `amountMinorUnits` (via `Money`), and a **Standing order** / **Direct Debit** tag from `type`. The backend returns occurrences date-ascending; render them in that order, and render each occurrence of a multiple-times-a-month instrument as its own row.

Each row links back to its instrument via a new pure helper `forecastOccurrenceHref(occurrence)`: `STANDING_ORDER → /move/standing-orders#<instrumentId>`, `DIRECT_DEBIT → /move/direct-debits#<instrumentId>`. To make the fragment land on the right instrument, add `id={standingOrderId}` and `id={directDebitId}` to `StandingOrderRow` and `DirectDebitRow` (reusing the existing `PayeeRow` anchor mechanism — native browser fragment-scroll, no JS, no highlight). Multiple occurrences of one instrument all link to the same anchor, which is correct.

Add a defensive e2e smoke (`forecast.spec.ts`) that navigates to the forecast screen and asserts the hero and breakdown render and the timeline shows either populated sections or the clean empty state — mirroring `move-money-dd-so.spec.ts`'s populated-or-empty-or-error branching, so it stays robust against the unpinnable deployed clock and non-deterministic seeded data.

## Acceptance criteria

- [ ] Expected and Completed sections render as native `<details>` disclosures with counts in their summaries; Expected is open by default, Completed collapsed; both toggle by keyboard and screen reader.
- [ ] Each row shows date, counterparty name, signed amount, and a Standing order / Direct Debit tag; rows are in date-ascending order; a multiple-occurrence instrument produces one row per occurrence.
- [ ] An empty Expected or Completed section shows explanatory copy rather than a blank region.
- [ ] `forecastOccurrenceHref` maps `STANDING_ORDER`/`DIRECT_DEBIT` to `/move/standing-orders#<id>` / `/move/direct-debits#<id>`; rows link via it.
- [ ] `StandingOrderRow` and `DirectDebitRow` carry `id={instrumentId}` so the fragment scrolls to the instrument on the list page; the existing SO/DD list behaviour is otherwise unchanged.
- [ ] Co-located unit tests: `forecastOccurrenceHref` (type→path); `ForecastOccurrenceRow` (parts + link href); `ForecastPage` extended for the Completed/Expected split, counts, default open/closed states, ordering, multiple-occurrence rows, empty-section copy, and row deep-links (with an injected response + fixed `now`). Prior art: `StandingOrderRow.test.tsx`, `SpendingPage.test.tsx`, the `move-money` pure-helper tests.
- [ ] `forecast.spec.ts` e2e smoke passes against the local stack (hero + breakdown render, timeline populated-or-empty).
- [ ] `npm test`, `npm run lint`, `npm run prettier`, `npm run a11y`, and (with the local stack up) `npm run test:e2e` for the new spec pass. No comments in code, including tests.

## Blocked by

- `02-forecast-screen-hero-and-breakdown` — the timeline mounts on the forecast screen and page introduced there.
