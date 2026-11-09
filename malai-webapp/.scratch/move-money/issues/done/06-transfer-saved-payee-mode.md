# Transfer — Saved-payee mode

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

Activate the **Saved payee** tab on the Transfer form. The slice-05 scaffolding already laid the source dropdown, the three-tab control, the AmountInput, the reference field, the two-step submit, and the error-mapping infrastructure — this slice only fills in the Saved-payee tab's body.

- Replace the _"Coming soon"_ placeholder content of the **Saved payee** tab with:
  - A **Payee picker** — the list of `usePayees()` data, alphabetical by `nickname ?? name`, rendered as selectable rows. Each row shows the same affordances as `PayeeRow` on `/move/payees` (name, optional nickname, masked sort-code/account-number).
  - Selecting a payee highlights it (radio-group semantics for a11y), and fills hidden destination state (`toSortCode`, `toAccountNumber`, and the displayed counterparty name) from the selected `PayeeResponse`.
  - Empty state when `usePayees()` returns nothing: _"No saved payees yet."_ with a link to `/move/payees/new` (_"Add a payee"_). Selecting a payee is required before _Continue_ enables.
- **Amount** and **Reference** fields render below the picker, always visible — _no_ intermediate step between "pick payee" and "enter amount".
- **No COPA call** on this path. The saved Payee was already verified at save time (slice 03's COPA-on-save path).
- Confirmation step (already built in slice 05) renders the counterparty name from the selected payee.

## Acceptance criteria

- [ ] The Saved-payee tab is enabled in `TransferPage`; the previous _"Coming soon"_ placeholder is removed.
- [ ] The payee picker uses radio-group semantics (`role="radiogroup"` + `role="radio"` per row, or the equivalent native `<input type="radio">` pattern); selecting a payee fills hidden destination state.
- [ ] Amount and Reference fields are always visible below the picker; the _Continue_ button stays disabled until a payee is selected.
- [ ] Empty state copy renders verbatim when `usePayees()` returns `[]`; the _"Add a payee"_ link routes to `/move/payees/new`.
- [ ] **No COPA call** is fired on this path — verified via a network assertion in the E2E spec (no `POST /payees/confirmation-of-payee` between _Continue_ and _Send_).
- [ ] Confirmation step renders the selected payee's name (or nickname-resolved display name) as the counterparty.
- [ ] On successful POST, source account's `useAccount` / `useBalance` / `useTransactions` queries are invalidated and the form resets (inherits behaviour from slice 05).
- [ ] Playwright E2E spec covers: signed-in user → `/move/send` → Saved-payee tab → pick a payee → enter amount → confirm → send → success → asserted invalidation. Plus an empty-state assertion when no payees are saved.
- [ ] `tests/e2e/a11y.spec.ts` extended to scan the Saved-payee tab in both populated and empty states.
- [ ] No comments anywhere — including tests.

## Blocked by

- 03-payees-list-add-with-copa
- 05-transfer-my-accounts-mode
