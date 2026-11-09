# Move money pages polish: PayeesPage, DirectDebitsPage, StandingOrdersPage

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The remaining three Move-money pages brought onto the same card-and-row visual treatment used by the redesigned Transaction list. After this slice lands, every list surface in the Move-money section feels like part of the same app — the Payees list, the read-only Direct Debits list, and the read-only Standing Orders list all share one row pattern, one empty-state treatment, and one money rendering.

- **PayeesPage**: each saved **Payee** rendered as a row — initial avatar (left), `nickname ?? name` (first line), masked sort-code + account-number (second line, muted). The existing Add Payee CTA and Delete affordance (with the Standing-Order cross-check modal from [ADR-0009](../../docs/adr/0009-payees-no-edit-no-fake-patch.md)) are preserved verbatim.
- **DirectDebitsPage**: each Direct Debit rendered as a row — creditor icon (left), creditor name (first line), next-collection date or "_Awaiting first collection_" (second line, muted), amount on the right. Variable-amount DDs render the literal word **"Variable"** (never `£0.00`). Sort order from the prior slice preserved.
- **StandingOrdersPage**: each Standing Order rendered as a row — destination icon (left), `payeeName` linkified to `/move/payees#{payeeId}` when `payeeId` is non-null and resolves in the loaded payees list (otherwise plain text), schedule summary (second line, muted), amount on the right via `<Money>`.
- **Empty states** for all three lists: designed empty state (icon + headline + sub-copy). PayeesPage empty state includes an inline "Add a payee" CTA. The two read-only lists' empty states do not (those surfaces are read-only).
- No contract changes. No new affordances. This slice is layout polish on top of existing data flows.

## Acceptance criteria

- [ ] PayeesPage rows render the avatar + nickname/name + masked identifiers pattern; Add Payee CTA and Delete-with-SO-cross-check modal continue to behave as in the Move-money slice.
- [ ] DirectDebitsPage rows render the icon + creditor + next-collection treatment; variable DDs show "Variable" not `£0.00`; sort order preserved.
- [ ] StandingOrdersPage rows render the icon + payee-link + schedule treatment; linked `payeeName` continues to anchor-scroll the Payees hub.
- [ ] All three pages use `<Money>` for amount rendering with `tabular-nums`.
- [ ] Each page has a designed empty state; only PayeesPage's empty state offers an inline CTA.
- [ ] Existing component tests for the three pages updated for the new row treatment; existing flows (delete, SO cross-check, payee-link anchor scroll) keep passing without behavioural regressions.
- [ ] axe-core a11y spec extended over the three redesigned pages without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md)
- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md)
- [03-money-balance-accounts-page](./03-money-balance-accounts-page.md) (uses `<Money>`)
