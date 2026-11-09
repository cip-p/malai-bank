# 01 — Dashboard forecast card

Status: done

## Parent

`.scratch/balance-forecast/PRD.md`

## What to build

The first end-to-end slice of the **Balance forecast**: a card on the account detail screen that shows where the account's balance is heading by month-end, reading the new `GET /api/v1/accounts/{accountUid}/forecast` endpoint.

Refresh this repo's `./openapi.yaml` snapshot from the backend source-of-truth and regenerate the client so the `BalanceForecastResponse` / `ForecastOccurrence` / `ForecastInstrumentType` types exist. Add a `useForecast(accountUid)` hook — the twin of `useSpendingBreakdown`, but with **no month param** (a forecast is always the current month) — that injects the **Active AccountHolder** header, gates on holder readiness, and maps a `503` to an `isUnavailable` state (the endpoint fails closed).

Add a `ForecastCard` and mount it on the account detail screen directly below the balance hero and above the action CTAs. The card renders one of: a loading skeleton; a compact "Balance forecast — unavailable just now" state on `503`; an empty state (forecast equal to the available balance, with a "No payments due this month" note and no warning) when there are no occurrences either side; or the populated state — "Balance forecast", "for {windowEnd}" (e.g. "for 31 July"), and the signed `forecastBalanceMinorUnits`. When that number is negative the card shows the warning treatment: the `destructive` (red) token plus a `TriangleAlert` icon — deliberately not the reference's amber, because malai has no arranged overdraft and a negative forecast is genuine overspend. The card carries a "View forecast" affordance linking to `/accounts/:accountUid/forecast` (the target route lands in slice 02; until then it resolves to the not-found page, which is acceptable).

Follow the Spending precedent for structure (hook feeding a presentational component, `503→unavailable`), the `Money` component for the signed amount, `date-fns` for the month-end label, and ADR-0003 token discipline for colour.

## Acceptance criteria

- [ ] `./openapi.yaml` snapshot refreshed from the backend and `npm run openapi:gen` run; the generated client exposes the forecast query and types.
- [ ] `useForecast(accountUid)` returns `{ forecast, isPending, isError, isUnavailable, error }`; the query is disabled until the Active AccountHolder is ready and sends the `MAL-Account-Holder-Id` header; a `503` sets `isUnavailable`.
- [ ] `ForecastCard` renders the populated state with the "Balance forecast" title, a "for {windowEnd}" sub-label, and the signed forecast balance.
- [ ] A negative forecast balance shows the `destructive`-token warning treatment with a `TriangleAlert` icon; a positive one shows no warning.
- [ ] The empty case (no occurrences) shows the forecast number equal to the available balance with a "No payments due this month" note and no warning.
- [ ] A `503` shows the compact "unavailable" card; the loading state shows a skeleton. The card never conditionally disappears when the query resolves.
- [ ] The card is mounted below the hero and above the CTA row on the account detail screen, with a "View forecast" link to `/accounts/:accountUid/forecast`.
- [ ] Co-located unit tests: `useForecast` (holder gating, `503→isUnavailable`, success/error shapes) and `ForecastCard` (loading, unavailable, empty, positive, negative states). Prior art: `useSpendingBreakdown.test.tsx`, `AccountHeroCard.test.tsx`.
- [ ] `CONTEXT.md` glossary terms **Balance forecast** and **Completed / Expected (forecast payments)** are already present; no copy contradicts them (no "arranged overdraft", no "budget").
- [ ] `npm test`, `npm run lint`, and `npm run prettier` pass. No comments in code, including tests.

## Blocked by

- None — can start immediately.
