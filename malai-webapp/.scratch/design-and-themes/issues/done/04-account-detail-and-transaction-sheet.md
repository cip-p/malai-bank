# AccountDetailPage redesign + TransactionDetailSheet + grouping helpers

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The next click after the AccountsPage hero. After this slice lands, tapping an Account card opens a redesigned page with a date-grouped transaction list, category icons, and a tap-to-open detail sheet that surfaces the running balance without cluttering the row.

- **`groupTransactionsByDate`** — pure function `(items: TransactionItemResponse[]) => [{ dateKey, label, items }]` ordered newest-first. Today's items go under "Today", yesterday's under "Yesterday", everything older under a formatted date (e.g. "5 June 2026") using `date-fns` against the User's local timezone. Empty input returns `[]`.
- **`resolveCategoryIcon`** — pure mapping `(category) => LucideIcon`. Unknown / undefined → default icon (e.g. `Receipt`). Single switch.
- **`<TransactionDetailSheet>`** — shadcn `Sheet` configured with `side="bottom"` below `md:` and `side="right"` at `≥md:`. Drag-to-dismiss on mobile, Escape and backdrop tap always. Focus is trapped while open and returned to the originating row on close. Renders the full detail of one Transaction including the running balance, full description, counterparty, merchant, category, and timestamp.
- **AccountDetailPage** redesigned:
  - **Account header**: account display name, large balance via `<Balance>` (count-up on first paint, motion-safe), masked sort code + account number, and a prominent **Send money** CTA that links to `/move/send?from={accountUid}` (the existing `?from=` behaviour from the Move-money slice continues to work).
  - **Transaction list**: rendered via `groupTransactionsByDate(transactions)` with sticky date headers. Each row: category icon (left), counterparty (first line), description (second line, muted), signed `<Money>` (right, direction-coloured, tabular-nums). Tapping a row opens the sheet.
  - **Empty state**: when an Account has zero Transactions, render a designed empty state (icon + headline + sub-copy) rather than a blank list.
  - **100-row footer note**: when the API returns exactly 100 Transactions, append "_Showing the 100 most recent — older transactions are not yet available in malai._" below the list. Not shown for smaller histories.
  - The running balance lives **only** inside `TransactionDetailSheet`. The transaction-list row never shows it.

## Acceptance criteria

- [ ] `groupTransactionsByDate` Vitest unit tests cover: today / yesterday / older grouping; newest-first ordering within each group; empty input → empty output; locale-correct date label format.
- [ ] `resolveCategoryIcon` Vitest unit tests cover: known category → expected icon; unknown category → default; undefined → default.
- [ ] AccountDetailPage shows the account header with `<Balance>` (animated under motion-safe, immediate under reduce) and the `?from=` Send money CTA.
- [ ] Transaction list renders with sticky date headers, category icons, counterparty + description + signed money per row, in newest-first order.
- [ ] Tapping a row opens `<TransactionDetailSheet>`; the sheet is `side="bottom"` on mobile, `side="right"` on desktop.
- [ ] `<TransactionDetailSheet>` displays the running balance, full description, counterparty, merchant, category, timestamp.
- [ ] Sheet dismisses via backdrop tap, Escape, and (mobile) drag-down. Focus is trapped while open and returned to the originating row on close — Vitest component test asserts focus return.
- [ ] Empty state renders for Accounts with zero Transactions.
- [ ] 100-row footer note renders only when exactly 100 Transactions are returned.
- [ ] AccountDetailPage component test updated to cover header, grouped list, row → sheet flow, empty state.
- [ ] axe-core a11y spec extended over the redesigned AccountDetailPage and the open sheet without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md)
- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md)
- [03-money-balance-accounts-page](./03-money-balance-accounts-page.md) (uses `<Money>` and `<Balance>`)
