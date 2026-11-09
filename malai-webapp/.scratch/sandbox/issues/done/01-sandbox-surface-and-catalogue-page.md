# Sandbox surface, routes, nav entries, and Counterparties page (read-only table)

Status: ready-for-agent

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

The first tracer bullet through the Sandbox webapp: a signed-in **User** finds **Sandbox** in the **Profile tab** (mobile) or in the desktop user-menu dropdown, clicks through, and lands on `/sandbox/counterparties` rendering the catalogue from `GET /api/v1/sandbox/counterparties`. The page is read-only in this slice — no action buttons per row, no inbound form, no deep-link prefill. Those land in issues 02 and 03 on top of the layout established here.

End-to-end behaviour:

- Refresh `openapi.yaml` from the backend (which has merged backend issue 01), regenerate the typed client via `npm run openapi:gen`.
- New `/sandbox` route. Bare `/sandbox` redirects to `/sandbox/counterparties`. The two-tab sub-nav (**Counterparties** + **Inbound payments**) is rendered by a `SandboxLayout` component. The **Inbound payments** tab is registered as a route now and renders a temporary placeholder ("Coming next") so the sub-nav has somewhere to point — the working form lives in issue 02.
- `CounterpartiesPage` fetches the catalogue via a new `useCounterparties` TanStack Query hook around the generated client. Renders:
  - A short header sentence: _"Type the name shown to get **MATCH**. Type a near-miss to get **CLOSE_MATCH**. Type anything else to get **NO_MATCH**."_
  - A table with columns: `Bank`, `Name on record`, `Sort code`, `Account number`, `Outcome`.
  - `Outcome` rendering rule: `SETTLES` → _"Settles"_; `RETURNS` → _"Returns ({returnReason})"_; `STUCK_PENDING` → _"Stays in progress"_.
  - Loading state matching the existing convention (skeleton or spinner — match what `AccountsPage` does).
  - Error state with a retry affordance matching the existing convention.
- `AppShell` updates: a **Sandbox** entry in the mobile **Profile tab** between Settings and Theme; a **Sandbox** row in the desktop user-menu dropdown between Settings and Sign out. The mobile bottom-tab strip stays at three per [ADR-0010](../../../docs/adr/0010-bottom-tabs-mobile-top-nav-desktop.md).
- Sort-code rendering uses the existing formatting helper (the one that produces `12-34-56`); account-number rendering uses the existing convention. Look up the helper used by `PayeesPage` and reuse it — do not invent a new one.

Architectural references already written: [ADR-0016](../../../docs/adr/0016-sandbox-placement-peer-of-settings.md) (placement), [ADR-0017](../../../docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md) (why `Outcome` is a property of the row).

## Acceptance criteria

- [ ] `openapi.yaml` snapshot is refreshed from the backend. `npm run openapi:gen` succeeds and produces typed `getApiV1SandboxCounterparties` (or whatever the generated factory name is) under `src/api/generated/`.
- [ ] New routes registered in `src/App.tsx`: `/sandbox` (redirects to `/sandbox/counterparties`), `/sandbox/counterparties`, `/sandbox/inbound-payments` (placeholder). All three sit inside the authenticated route guard the existing app uses.
- [ ] New component `SandboxLayout` renders a two-tab sub-nav linking to `/sandbox/counterparties` and `/sandbox/inbound-payments`, plus an `<Outlet />` for the active tab's content. Tab labels match the locked vocabulary in `CONTEXT.md`.
- [ ] New `useCounterparties` hook in `src/sandbox/` wraps the generated TanStack Query factory; sends auth credentials per the existing convention.
- [ ] New `CounterpartiesPage` component renders:
  - the header sentence (exact copy as in the _What to build_ section);
  - a table with the five columns in order: `Bank`, `Name on record`, `Sort code`, `Account number`, `Outcome`;
  - `Outcome` cell formatted per the rules above (verify the rendered text matches each enum value with snapshot or by `getByText`);
  - loading state during the query (mirror the convention used by `AccountsPage`);
  - error state with a retry button.
- [ ] New `InboundPaymentsPlaceholderPage` (or equivalent) at `/sandbox/inbound-payments` renders a clear "Coming next" message so the sub-nav target is non-broken. (The working form lands in issue 02 and replaces this placeholder.)
- [ ] `AppShell.tsx` modified: mobile **Profile tab** gains a Sandbox link between Settings and Theme; desktop user-menu dropdown gains a Sandbox row between Settings and Sign out. Both link to `/sandbox` (which redirects to `/sandbox/counterparties`). Data-testids follow the existing `data-testid="user-menu-item-..."` pattern.
- [ ] Bottom-tab strip on mobile remains three items (Accounts / Move / Profile) — no fourth tab.
- [ ] `CounterpartiesPage.test.tsx` (Vitest + Testing Library) covers: loading state; rows render with all five columns once the query resolves; `Outcome` formatting branches for `SETTLES` / `RETURNS` (asserting the `returnReason` is shown in the parens) / `STUCK_PENDING`; header sentence is present; error state renders on query failure.
- [ ] `SandboxLayout.test.tsx` covers: both tab links are rendered; the active tab is visually distinct; placeholder content renders on `/sandbox/inbound-payments`.
- [ ] `AppShell.test.tsx` extended: asserts the Sandbox link is present in the Profile tab and in the desktop user-menu dropdown, and that the mobile bottom-tab strip still contains exactly three items.
- [ ] Playwright spec `tests/e2e/sandbox.spec.ts` (or extension of the existing auth-aware E2E suite) covers: sign in via the shared auth fixture, open the Profile tab, click Sandbox, see the Counterparties table, assert at least one row carries each of the three `Outcome` formattings (use the seeded fixtures from backend issue 01 as the source of truth).
- [ ] `tests/e2e/a11y.spec.ts` (or equivalent) is extended to cover `/sandbox/counterparties` per [ADR-0006](../../../docs/adr/0006-a11y-baseline.md).
- [ ] `npm test`, `npm run test:e2e`, `npm run lint`, `npm run prettier` all pass.

## Blocked by

- [`malai-backend/.scratch/sandbox/issues/01-catalogue-get-end-to-end.md`](../../../../malai-backend/.scratch/sandbox/issues/01-catalogue-get-end-to-end.md) — needs the backend's `GET /api/v1/sandbox/counterparties` endpoint to exist and the `openapi.yaml` to carry the schemas.
