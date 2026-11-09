# AccountDetailPage: Send-money CTA + Scheduled-payments peek

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

Two additions to the existing `AccountDetailPage` that surface the new Move-money primitives in per-account context. Reuses the `useDirectDebits` / `useStandingOrders` hooks from slice 02 and the `?from=<accountUid>` query handling from slice 05. No new pages.

- **`SendMoneyCta`** — primary-variant `Button` rendered prominently on `AccountDetailPage`, labelled _"Send money"_, linking to `/move/send?from=<accountUid>`. Positioned near the Account hero (top of the page), per the UK retail-banking convention where the per-account "send money" affordance is the highest-utility action. The link uses the existing `Link` primitive from `react-router-dom`.
- **`ScheduledPaymentsPeek`** — a new section on `AccountDetailPage`, below the transactions list, headed _"Scheduled payments on this account"_. Renders **two distinct sub-lists**:
  - **Direct Debits** — `useDirectDebits().data` filtered client-side to `dd.accountId === thisAccount.accountId`. Reuses `DirectDebitRow` from slice 02. Empty state: _"No direct debits on this account."_ Footer: _"View all"_ link to `/move/direct-debits`.
  - **Standing Orders** — `useStandingOrders().data` filtered client-side to `so.accountId === thisAccount.accountId`. Reuses `StandingOrderRow` from slice 02. Empty state: _"No standing orders on this account."_ Footer: _"View all"_ link to `/move/standing-orders`.
- The two sub-lists are visually distinct (separate sub-headers, separate empty states) — never merged into one "scheduled payments" list. Per **CONTEXT.md**'s explicit _"avoid grouping DD and SO under one screen"_ rule for the canonical hub screens, repeated here on the per-account context for consistency. The grouping label _"Scheduled payments on this account"_ is acceptable because the framing is "_what regular activity affects this account?_", not a denial of the underlying product distinction — the rendered sub-lists are still separately labelled.
- Loading skeletons inherit from slice 02's row components.
- If the User has only one reachable Account, the account chip inside the row components is hidden (slice 02's behaviour) — `ScheduledPaymentsPeek` does not need its own variant logic.

## Acceptance criteria

- [ ] `SendMoneyCta` renders on `AccountDetailPage`, near the Account hero, as the primary-variant CTA; href is `/move/send?from=<accountUid>` with the current account's UID; clicking it navigates and pre-selects the source account in `TransferPage` (verified through slice 05's existing `?from=` handling).
- [ ] `ScheduledPaymentsPeek` renders below the transactions list with the heading _"Scheduled payments on this account"_.
- [ ] The DD sub-list filters to `accountId === thisAccount.accountId`; the SO sub-list does the same; client-side `.filter` on the already-loaded query data (no extra request).
- [ ] Each sub-list has its own sub-header (_"Direct Debits"_ / _"Standing Orders"_) and its own empty state when the filter produces no rows.
- [ ] Each sub-list has a _"View all"_ link routing to its respective `/move/*` hub.
- [ ] Loading skeletons render consistently with the slice-02 row skeletons.
- [ ] Component tests assert: SendMoneyCta href contains the correct `?from=`; ScheduledPaymentsPeek with empty data shows both empty states; with mixed data filters correctly per accountId.
- [ ] Playwright E2E spec covers: signed-in user on `/accounts/<id>` clicks _Send money_ → lands on `/move/send` with source pre-selected; scrolls to the Scheduled-payments peek; asserts the right rows render per account; clicks _View all_ on each sub-list and confirms navigation.
- [ ] `tests/e2e/a11y.spec.ts` re-scans `AccountDetailPage` to cover the new section.
- [ ] No comments anywhere — including tests.

## Blocked by

- 02-direct-debits-standing-orders-read-only
- 05-transfer-my-accounts-mode
