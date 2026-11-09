# Read-only Direct Debits and Standing Orders

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

Two read-only list screens that surface the recurring activity flowing through the **Active AccountHolder**'s **Accounts**, plus the shared utilities both screens (and the later `AccountDetailPage` peek in slice 08) will reuse.

- **`useDirectDebits`** hook over `GET /api/v1/direct-debits`; **`useStandingOrders`** hook over `GET /api/v1/standing-orders`. Both follow the existing `useAccounts` pattern (TanStack-Query factory + thin wrapper) and include the `MAL-Account-Holder-Id` header for the **Active AccountHolder**.
- **`sortByNextDate`** pure helper: `<T extends { nextDate: string | null; name: string }>(items: T[]) => T[]`. Ascending by date, nulls bucketed to the bottom and alphabetised by `name` within the null bucket.
- **`formatNextDate`** pure helper: `(iso: string | null, now: Date) => string`. Returns:
  - `null` → `"Next: not scheduled"`
  - within 7 days → `"Next: in {N} days"` (singular `1 day`)
  - current calendar year → `"Next: 23 May"`
  - any other year → `"Next: 23 May 2025"`
- **`DirectDebitRow`** — primary label `creditorName`, secondary line `reference` (when present), amount right-aligned. Amount renders **the literal word "Variable"** in muted weight when `amountMinorUnits` is `null`; otherwise `formatCurrency(amountMinorUnits, currency)`. Frequency label title-cased (`Weekly` / `Monthly` / `Quarterly` / `Annually`). Next-date caption via `formatNextDate`. Account chip rendered **only when** the User has ≥2 reachable **Accounts**.
- **`StandingOrderRow`** — same shape as `DirectDebitRow`, with `payeeName` as primary label. When `payeeId` is non-null **and** resolves to an entry in `usePayees()`, render `payeeName` as a link to `/move/payees#${payeeId}`. Otherwise plain text. In this slice **Payees CRUD does not exist yet**, so `usePayees` will return an empty list and every `payeeName` renders as plain text; the link wiring activates automatically when slice 03 ships.
- **`DirectDebitsPage`** and **`StandingOrdersPage`** replace the slice-01 placeholders. Loading state renders skeletons matching the eventual row shape (mirror the `TransactionsList` skeleton pattern). Empty states render the explanatory copy from the PRD and **no Create CTA**.

## Acceptance criteria

- [ ] `useDirectDebits` and `useStandingOrders` are TanStack-Query hooks following the existing `useAccounts` pattern; both send the `MAL-Account-Holder-Id` header; query keys are stable enough to be invalidated by future mutations.
- [ ] `sortByNextDate` unit tests cover: empty array, all nulls (alphabetised), mixed nulls+dates (nulls bucketed to bottom), ties on date (alphabetical fallback), single-element array.
- [ ] `formatNextDate` unit tests cover: null, today, +1 day, +6 days, +7 days, +30 days in current year, prior calendar year, future calendar year.
- [ ] `DirectDebitsPage` renders all DDs sorted by `nextCollectionAt` asc. Variable-amount DDs render `"Variable"`, never `£0.00`. Null `nextCollectionAt` renders `"Next: not scheduled"`. Empty state matches PRD copy and shows no Create CTA.
- [ ] `StandingOrdersPage` renders all SOs sorted by `nextPaymentAt` asc with the same empty-state discipline.
- [ ] Account chip rendered only when the user has ≥2 reachable Accounts (verified via the existing account-holder query); with one Account, no chip on any row.
- [ ] Playwright E2E spec covers: signed-in user navigates to `/move/direct-debits`, sees populated list (or empty state on the test fixture's behalf); navigates to `/move/standing-orders`, same; row shape matches the PRD per assertion.
- [ ] `tests/e2e/a11y.spec.ts` extended to scan both pages.
- [ ] No comments anywhere — including tests.

## Blocked by

- 01-scaffold-move-money-section
