# 02 — Forecast screen: hero + breakdown

Status: ready-for-agent

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

The dedicated **Balance forecast** screen, reached from the card's "View forecast" link. Add an authenticated route `/accounts/:accountUid/forecast` (peer of the existing `/accounts/:accountUid/spending`) rendering a `ForecastPage`.

The page leads with a **forecast hero**: the big signed `forecastBalanceMinorUnits`, headed "Balance forecast" with a "for {windowEnd}" label and the window range (e.g. "1 July to 31 July"). When the forecast is negative the hero uses the warning treatment (the `destructive` token + `TriangleAlert`), matching the card. Below the hero sits a compact **breakdown** showing the `availableBalanceMinorUnits` and the `expectedTotalMinorUnits`, which visibly reconcile to the forecast (available balance plus expected payments). The page reuses `useForecast` from slice 01 and honours its states: a loading skeleton while fetching, and a fail-closed "forecast unavailable" state on `503` (mirroring the Spending screen's unavailable branch) rather than a partial view.

There is **no month navigation** — the forecast is always the current month. The page takes a `now?` prop (as `SpendingPage` does) purely so tests can pin date formatting deterministically. The timeline of individual payments is added in slice 03; this slice may leave a placeholder or omit the timeline region.

Wire the `ForecastCard`'s "View forecast" link (added in slice 01) to this route so the card-to-screen path works end to end. Follow `SpendingPage` for structure, the `Money` component for signed amounts, and `date-fns` for date labels.

## Acceptance criteria

- [ ] Route `/accounts/:accountUid/forecast` renders `ForecastPage` for authenticated users.
- [ ] The hero shows the signed `forecastBalanceMinorUnits`, the "Balance forecast" heading, a "for {windowEnd}" label, and the window range; a negative value shows the `destructive` warning treatment with a `TriangleAlert`.
- [ ] The breakdown shows available balance and the expected-payments total, and the two reconcile to the forecast headline.
- [ ] Loading shows a skeleton; a `503` shows a fail-closed "forecast unavailable" state (no partial render).
- [ ] The page accepts a `now?` prop used only for deterministic date formatting; production uses the response's window and the real clock for display.
- [ ] The card's "View forecast" link navigates here; a back link returns to the account detail screen.
- [ ] The `/accounts/:accountUid/forecast` route is added to the axe-core sweep in `a11y.spec.ts` and passes.
- [ ] Co-located unit tests for `ForecastPage`: hero number + negative-warning styling, breakdown figures, loading and unavailable branches, with an injected response and fixed `now`. Prior art: `SpendingPage.test.tsx`.
- [ ] `npm test`, `npm run lint`, `npm run prettier`, and `npm run a11y` pass. No comments in code, including tests.

## Blocked by

- `01-dashboard-forecast-card` — introduces the refreshed contract, `useForecast`, and the card link this route is wired to.
