# Counterparty deep-link action buttons + AddPayeePage / TransferPage prefill

Status: ready-for-agent

## Parent

[`.scratch/sandbox/PRD.md`](../PRD.md)

## What to build

Add the three deep-link action buttons per row on the **Counterparties** tab and the query-param prefill plumbing they target, completing the catalogue-first IA and closing out the brief's three asks. After this slice ships, a signed-in **User** clicking any button on a catalogue row lands in the real customer-facing flow (Add Payee, Transfer, or the Inbound payments tab) with the right destination pre-populated and the fields editable.

End-to-end behaviour:

- New pure helper `counterpartyDeepLink(counterparty, target)` returns the path string for each of the three CTAs. Single source of truth for the URL shape; trivially unit-testable. The three targets:
  - `"save-as-payee"` → `/move/payees/new?sortCode=<SC>&accountNumber=<AN>&name=<NAME>`
  - `"send-a-payment"` → the Transfer flow URL that lands on the **New payee** tab with the same three params (verify the exact route path during implementation — likely `/move/send?…` or `/move/transfer?…` per the established move-money slice).
  - `"receive-a-payment"` → `/sandbox/inbound-payments?originatorSortCode=<SC>&originatorAccountNumber=<AN>&originatorName=<NAME>`
- All three buttons render per row on `CounterpartiesPage`. Button copy: **Save as payee** · **Send a payment** · **Receive a payment**. URL-encode the `name` param via `encodeURIComponent` — names contain spaces, apostrophes, etc.
- `AddPayeePage` reads `?sortCode=…&accountNumber=…&name=…` on mount and pre-fills the three matching form fields. All three fields stay editable so the user can demo `CLOSE_MATCH` (mutate the name) or `NO_MATCH` (replace the name entirely). The existing CoP-on-submit flow runs unchanged.
- `TransferPage` reads similar query params and pre-fills the **New payee** tab. The source-account dropdown's existing default behaviour is preserved (whatever `move-money` slice 06 set up); only the destination fields gain prefill.
- The `"Receive a payment"` deep-link feeds the originator picker on `/sandbox/inbound-payments`. The consumption side of that link is already implemented in issue 02; this slice produces the link.

Architectural references already written: [ADR-0017](../../../docs/adr/0017-sandbox-counterparty-outcome-destination-deterministic.md) (why the table shows deterministic outcomes), [ADR-0009](../../../docs/adr/0009-payees-no-edit-no-fake-patch.md) (Save as payee is creation, not edit-of-existing).

## Acceptance criteria

- [ ] New pure helper `counterpartyDeepLink(counterparty, target: "save-as-payee" | "send-a-payment" | "receive-a-payment"): string` in `src/sandbox/`. URL-encodes the `name` param. Single source of truth for all three URL shapes.
- [ ] `counterpartyDeepLink.test.ts` unit-test covers each of the three targets, plus a name carrying a space and an apostrophe (asserting `%20` and `%27` make it through encoding).
- [ ] `CounterpartiesPage` renders three action buttons per row using the helper. Button copy: **Save as payee**, **Send a payment**, **Receive a payment**. Buttons render as links (so right-click "Open in new tab" works) with the URLs from the helper.
- [ ] `AddPayeePage.tsx` modified: on mount, reads `sortCode`, `accountNumber`, `name` from the URL search params and pre-fills the three matching form fields. All fields stay editable; the existing CoP-on-submit flow runs unchanged.
- [ ] `TransferPage.tsx` modified: on mount, reads the destination-side query params and pre-fills the **New payee** tab's three fields. The source-account dropdown's existing default behaviour (per move-money slice 06) is unchanged.
- [ ] `AddPayeePage.test.tsx` extended: arriving with the three query params populates the three fields; fields remain editable; the existing test for the no-params path still passes.
- [ ] `TransferPage.test.tsx` extended: arriving with the three query params populates the **New payee** tab's fields and selects that tab as active.
- [ ] `CounterpartiesPage.test.tsx` extended: three buttons render per row; clicking each (or asserting the `href`) lands at the correct path with correctly encoded params.
- [ ] Playwright spec extended (`tests/e2e/sandbox.spec.ts`): from `/sandbox/counterparties`, click **Save as payee** on a known row, land on `/move/payees/new` with three fields prefilled, submit, see the `MATCH` verdict, return to `/sandbox/counterparties`, click **Send a payment** on the same row, land on the Transfer flow's New payee tab with the destination pre-populated, return to `/sandbox/counterparties`, click **Receive a payment** on the same row, land on `/sandbox/inbound-payments` with the originator pre-selected, submit, see the new **Transaction** on the credited Account.
- [ ] `tests/e2e/a11y.spec.ts` continues to pass for `/sandbox/counterparties`, `/move/payees/new`, and the Transfer flow.
- [ ] `npm test`, `npm run test:e2e`, `npm run lint`, `npm run prettier` all pass.

## Blocked by

- [`./01-sandbox-surface-and-catalogue-page.md`](./01-sandbox-surface-and-catalogue-page.md) — needs the Counterparties tab and the catalogue to exist.
- [`./02-inbound-payments-page.md`](./02-inbound-payments-page.md) — the **Receive a payment** button's deep-link target is the working inbound form; needs the deep-link consumption code in `InboundPaymentsPage` to be in place.
