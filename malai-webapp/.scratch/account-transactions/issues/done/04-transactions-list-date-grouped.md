# Transactions list with date grouping

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Account detail page + transactions list

## What to build

The detail page gains a `<TransactionsList accountUid />` beneath the hero. It wraps a new `useTransactions(accountUid)` hook (over the generated factory for `GET /api/v1/accounts/{accountUid}/transactions`, injecting `MalAccountHolderIdHeader`) and renders the most recent **Transactions** the API returns. The API caps at 100 newest-first with no pagination; the hook does not paginate.

Two new pure helpers are added (no React, no I/O — both unit-testable in isolation):

- **`formatRelativeDateGroup(date, now)`** returns the group label: `"Today"` for a date matching `now`, `"Yesterday"` for one calendar day before, `"14 May"` for an earlier date in the current year, `"14 May 2024"` for a date in a prior year. Uses `date-fns` (already in `package.json`). `now` is passed explicitly so tests are deterministic.
- **`groupTransactionsByDate(transactions, now)`** returns an ordered `Array<{ label: string; items: TransactionItemResponse[] }>`, preserving the API's newest-first ordering within each group. Each group's `label` comes from `formatRelativeDateGroup` on the group's date.

`<TransactionRow transaction />` renders a single **Transaction** per CONTEXT.md's canonical definition (one ledger entry; never deduplicated by `transactionId`): primary label `merchantName ?? description ?? "Transaction"`; muted secondary line of `category` when present; right-aligned signed amount via `formatCurrency` (e.g. `−£12.50`, `+£2,000.00`); right-aligned muted running balance underneath as `Bal {formatCurrency(...)}`. The `counterpartyId` field is not rendered — it is a bare UUID with no resolution endpoint. Rows are not interactive.

`<TransactionsList>` renders:

- Section heading `Transactions` always present (above any state).
- While pending: a skeleton of three pulsing row placeholders with `aria-busy="true"` and a meaningful `aria-label`.
- When the API returns zero entries: the single muted line `"No transactions yet."`.
- On query error: the inline muted line `"Couldn't load transactions"` (in addition to the global ADR-0005 toast).
- Otherwise: the date-grouped list, with the group label rendered as a small subheading above each block of rows.

Per the PRD, partial-failure rendering (transactions errors while balance + identifiers succeed) is exercised by unit tests on `<TransactionsList>`'s error state and the page composition — not by an E2E variant that would have to fault-inject against the real backend.

## Acceptance criteria

- [ ] `useTransactions(accountUid)` wraps the generated query for `GET /api/v1/accounts/{accountUid}/transactions` and injects `MalAccountHolderIdHeader`.
- [ ] `formatRelativeDateGroup(date, now)` returns the agreed labels for today / yesterday / current-year / prior-year cases.
- [ ] `groupTransactionsByDate(transactions, now)` returns an ordered array of `{ label, items }`, preserving newest-first ordering within groups.
- [ ] `<TransactionRow>` renders the agreed anatomy: `merchantName ?? description ?? "Transaction"` primary label, muted `category` secondary when present, signed amount right-aligned, running balance line beneath.
- [ ] `<TransactionsList>` renders heading + (skeleton | empty line | inline error | date-grouped list) per state.
- [ ] Unit tests cover both pure helpers' edge cases (empty input, single group, multi-group, year boundaries).
- [ ] Unit/integration tests cover `<TransactionsList>`'s skeleton, empty, error, and success states.
- [ ] E2E asserts on a detail page with non-empty **Transactions**: at least one row shows a signed amount matching `/^[+−]£[\d,]+\.\d{2}$/`, a running balance line underneath, and at least one date-group heading is present.
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

[01-detail-page-route-and-cache-primed-hero.md](./01-detail-page-route-and-cache-primed-hero.md)
